// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

private val LOG = logger<ProjectRootManagerImpl>()

private val EP_NAME = ProjectExtensionPointName<ProjectExtension>("com.intellij.projectExtension")
private const val PROJECT_JDK_NAME_ATTR = "project-jdk-name"
private const val PROJECT_JDK_TYPE_ATTR = "project-jdk-type"
private const val ATTRIBUTE_VERSION = "version"

@State(name = "ProjectRootManager")
open class ProjectRootManagerImpl(val project: Project,
                                  private val coroutineScope: CoroutineScope) : ProjectRootManagerEx(), PersistentStateComponent<Element> {
  private val projectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener::class.java)
  private var projectSdkName: String? = null
  private var projectSdkType: String? = null
  private val rootCache: OrderRootsCache
  private var isStateLoaded = false

  init {
    @Suppress("LeakingThis")
    rootCache = getOrderRootsCache(project)
    project.getMessageBus().simpleConnect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        val currentName = projectSdkName
        if (previousName == currentName) {
          // if already had jdk name and that name was the name of the jdk just changed
          projectSdkName = jdk.getName()
          projectSdkType = jdk.getSdkType().getName()
        }
      }
    })
  }

  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectRootManagerImpl = getInstance(project) as ProjectRootManagerImpl

    fun extractLocalPath(url: String): String {
      val path = URLUtil.extractPath(url)
      val separatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR)
      return if (separatorIndex > 0) path.substring(0, separatorIndex) else path
    }
  }

  @ApiStatus.Internal
  abstract inner class BatchSession<Change, ChangeList>(private val fileTypes: Boolean) {
    private var batchLevel = 0
    private var pendingRootsChanged = 0
    private var isChanged = false
    private var changes: ChangeList? = null

    fun levelUp() {
      if (batchLevel == 0) {
        isChanged = false
        changes = null
      }
      batchLevel += 1
    }

    fun levelDown() {
      batchLevel -= 1
      if (isChanged && batchLevel == 0) {
        try {
          // todo make sure it should be not null here
          if (changes == null) {
            changes = initiateChangelist(genericChange)
          }
          pendingRootsChanged--
          ApplicationManager.getApplication().runWriteAction { fireRootsChanged(changes!!) }
        }
        finally {
          if (pendingRootsChanged == 0) {
            isChanged = false
            changes = null
          }
        }
      }
    }

    fun beforeRootsChanged() {
      if (batchLevel == 0 || !isChanged) {
        fireBeforeRootsChanged(fileTypes)
        pendingRootsChanged++
        isChanged = true
      }
    }

    @JvmOverloads
    fun rootsChanged(change: Change = genericChange) {
      changes = if (changes == null) initiateChangelist(change) else accumulate(changes!!, change)
      if (batchLevel == 0 && isChanged) {
        pendingRootsChanged--
        if (fireRootsChanged(changes!!) && pendingRootsChanged == 0) {
          isChanged = false
          changes = null
        }
      }
    }

    protected abstract fun fireRootsChanged(change: ChangeList): Boolean

    protected abstract fun initiateChangelist(change: Change): ChangeList

    protected abstract fun accumulate(current: ChangeList, change: Change): ChangeList

    protected abstract val genericChange: Change
  }

  @get:ApiStatus.Internal
  val rootsChanged: BatchSession<RootsChangeRescanningInfo, MutableList<RootsChangeRescanningInfo>> = object : BatchSession<RootsChangeRescanningInfo, MutableList<RootsChangeRescanningInfo>>(false) {
    override fun fireRootsChanged(change: MutableList<RootsChangeRescanningInfo>): Boolean {
      return this@ProjectRootManagerImpl.fireRootsChanged(fileTypes = false, indexingInfos = change)
    }

    override fun accumulate(current: MutableList<RootsChangeRescanningInfo>,
                            change: RootsChangeRescanningInfo): MutableList<RootsChangeRescanningInfo> {
      current.add(change)
      return current
    }

    override val genericChange: RootsChangeRescanningInfo
      get() = RootsChangeRescanningInfo.TOTAL_RESCAN

    override fun initiateChangelist(change: RootsChangeRescanningInfo) = SmartList(change)
  }

  protected val fileTypesChanged: BatchSession<Boolean, Boolean> = object : BatchSession<Boolean, Boolean>(true) {
    override fun fireRootsChanged(change: Boolean): Boolean {
      return this@ProjectRootManagerImpl.fireRootsChanged(true, emptyList())
    }

    override fun accumulate(current: Boolean, change: Boolean): Boolean {
      return current || change
    }

    override val genericChange: Boolean
      get() = true

    override fun initiateChangelist(change: Boolean): Boolean = change
  }
  open val rootsValidityChangedListener: VirtualFilePointerListener = object : VirtualFilePointerListener {}
  override fun getFileIndex(): ProjectFileIndex {
    return ProjectFileIndex.getInstance(project)
  }

  override fun getContentRootUrls(): List<String> {
    val modules = moduleManager.modules
    val result = ArrayList<String>(modules.size)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getContentRootUrls())
    }
    return result
  }

  override fun getContentRoots(): Array<VirtualFile> {
    val modules = moduleManager.modules
    val result = ArrayList<VirtualFile>(modules.size)
    for (module in modules) {
      val contentRoots = ModuleRootManager.getInstance(module).getContentRoots()
      if (modules.size == 1) {
        return contentRoots
      }
      result.addAll(contentRoots)
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  override fun getContentSourceRoots(): Array<VirtualFile> {
    val modules = moduleManager.modules
    val result = ArrayList<VirtualFile>(modules.size)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getSourceRoots())
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  override fun getModuleSourceRoots(rootTypes: Set<JpsModuleSourceRootType<*>?>): List<VirtualFile> {
    val modules = moduleManager.modules
    val roots = ArrayList<VirtualFile>(modules.size)
    for (module in modules) {
      roots.addAll(ModuleRootManager.getInstance(module).getSourceRoots(rootTypes))
    }
    return roots
  }

  override fun orderEntries(): OrderEnumerator = ProjectOrderEnumerator(project, rootCache)

  override fun orderEntries(modules: Collection<Module>): OrderEnumerator = ModulesOrderEnumerator(modules)

  override fun getContentRootsFromAllModules(): Array<VirtualFile> {
    val modules = moduleManager.sortedModules
    val result = ArrayList<VirtualFile>(modules.size + 1)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getContentRoots())
    }
    @Suppress("DEPRECATION")
    project.getBaseDir()?.let {
      result.add(it)
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  final override fun getProjectSdk(): Sdk? {
    if (projectSdkName == null) {
      return null
    }

    val projectJdkTable = ProjectJdkTable.getInstance()
    if (projectSdkType == null) {
      return projectJdkTable.findJdk(projectSdkName!!)
    }
    else {
      return projectJdkTable.findJdk(projectSdkName!!, projectSdkType!!)
    }
  }

  override fun getProjectSdkName(): String? = projectSdkName

  override fun getProjectSdkTypeName(): String? = projectSdkType

  override fun setProjectSdk(sdk: Sdk?) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (sdk == null) {
      projectSdkName = null
      projectSdkType = null
    }
    else {
      projectSdkName = sdk.getName()
      projectSdkType = sdk.getSdkType().getName()
    }
    projectJdkChanged()
  }

  fun projectJdkChanged() {
    incModificationCount()
    mergeRootsChangesDuring(actionToRunWhenProjectJdkChanges)
    fireJdkChanged()
  }

  private fun fireJdkChanged() {
    val sdk = getProjectSdk()
    for (extension in EP_NAME.getExtensions(project)) {
      extension.projectSdkChanged(sdk)
    }
  }

  protected open val actionToRunWhenProjectJdkChanges: Runnable
    get() = Runnable { projectJdkEventDispatcher.getMulticaster().projectJdkChanged() }

  override fun setProjectSdkName(name: String, sdkTypeName: String) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    projectSdkName = name
    projectSdkType = sdkTypeName
    projectJdkChanged()
  }

  override fun addProjectJdkListener(listener: ProjectJdkListener) {
    projectJdkEventDispatcher.addListener(listener)
  }

  override fun removeProjectJdkListener(listener: ProjectJdkListener) {
    projectJdkEventDispatcher.removeListener(listener)
  }

  override fun loadState(element: Element) {
    LOG.debug("Loading state into element")
    var stateChanged = false
    for (extension in EP_NAME.getExtensions(project)) {
      stateChanged = stateChanged or extension.readExternalElement(element)
    }

    val oldSdkName = projectSdkName
    val oldSdkType = projectSdkType
    projectSdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR)
    projectSdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR)
    if (oldSdkName != projectSdkName) stateChanged = true
    if (oldSdkType != projectSdkType) stateChanged = true
    val app = ApplicationManager.getApplication()
    LOG.debug { "ProjectRootManagerImpl state was changed: $stateChanged" }
    if (app != null) {
      val isStateLoaded = isStateLoaded
      if (stateChanged) {
        coroutineScope.launch(ModalityState.nonModal().asContextElement()) {
          applyState(isStateLoaded)
        }
      }
    }
    isStateLoaded = true
  }

  private suspend fun applyState(isStateLoaded: Boolean) {
    if (isStateLoaded) {
      LOG.debug("Run write action for projectJdkChanged()")
      writeAction {
        projectJdkChanged()
      }
      return
    }

    // prevent root changed event during startup to improve startup performance
    val projectSdkName = projectSdkName
    val sdk = if (projectSdkName == null) {
      null
    }
    else {
      val projectJdkTable = serviceAsync<ProjectJdkTable>()
      readActionBlocking {
        if (projectSdkType == null) {
          projectJdkTable.findJdk(projectSdkName)
        }
        else {
          projectJdkTable.findJdk(projectSdkName, projectSdkType!!)
        }
      }
    }

    LOG.debug("Run write action for extension.projectSdkChanged(sdk)")
    val extensions = EP_NAME.getExtensions(project)
    writeAction {
      for (extension in extensions) {
        extension.projectSdkChanged(sdk)
      }
    }
  }

  override fun noStateLoaded() {
    isStateLoaded = true
  }

  override fun getState(): Element? {
    val element = Element("state")
    element.setAttribute(ATTRIBUTE_VERSION, "2")
    for (extension in EP_NAME.getExtensions(project)) {
      extension.writeExternal(element)
    }
    if (projectSdkName != null) {
      element.setAttribute(PROJECT_JDK_NAME_ATTR, projectSdkName)
    }
    if (projectSdkType != null) {
      element.setAttribute(PROJECT_JDK_TYPE_ATTR, projectSdkType)
    }
    if (element.attributes.size == 1) {
      // remove an empty element to not write defaults
      element.removeAttribute(ATTRIBUTE_VERSION)
    }
    return element
  }

  override fun mergeRootsChangesDuring(runnable: Runnable) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val batchSession = rootsChanged
    batchSession.levelUp()
    try {
      runnable.run()
    }
    finally {
      batchSession.levelDown()
    }
  }

  protected open fun clearScopesCaches() {
    clearScopesCachesForModules()
  }

  override fun clearScopesCachesForModules() {
    rootCache.clearCache()
    for (module in ModuleManager.getInstance(project).modules) {
      ModuleRootManagerEx.getInstanceEx(module).dropCaches()
    }
  }

  @Deprecated("")
  override fun makeRootsChange(runnable: Runnable, fileTypes: Boolean, fireEvents: Boolean) {
    if (project.isDisposed()) {
      return
    }

    val session = if (fileTypes) fileTypesChanged else rootsChanged
    try {
      if (fireEvents) {
        session.beforeRootsChanged()
      }
      runnable.run()
    }
    finally {
      if (fireEvents) {
        session.rootsChanged()
      }
    }
  }

  override fun makeRootsChange(runnable: Runnable, changes: RootsChangeRescanningInfo) {
    if (project.isDisposed()) {
      return
    }
    try {
      rootsChanged.beforeRootsChanged()
      runnable.run()
    }
    finally {
      rootsChanged.rootsChanged(changes)
    }
  }

  override fun withRootsChange(changes: RootsChangeRescanningInfo): AutoCloseable {
    rootsChanged.beforeRootsChanged()
    return AutoCloseable { rootsChanged.rootsChanged(changes) }
  }

  var isFiringEvent: Boolean = false
    protected set

  private fun fireBeforeRootsChanged(fileTypes: Boolean) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.")
    fireBeforeRootsChangeEvent(fileTypes)
  }

  @ApiStatus.Internal
  protected open fun fireBeforeRootsChangeEvent(fileTypes: Boolean) {
  }

  private fun fireRootsChanged(fileTypes: Boolean, indexingInfos: List<RootsChangeRescanningInfo>): Boolean {
    if (project.isDisposed()) {
      return false
    }

    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.")
    clearScopesCaches()
    incModificationCount()
    fireRootsChangedEvent(fileTypes, indexingInfos)
    return true
  }

  @ApiStatus.Internal
  protected open fun fireRootsChangedEvent(fileTypes: Boolean, indexingInfos: List<RootsChangeRescanningInfo>) {
  }

  @ApiStatus.Internal
  protected open fun getOrderRootsCache(project: Project): OrderRootsCache = OrderRootsCache(project)

  private val moduleManager: ModuleManager
    get() = ModuleManager.getInstance(project)

  override fun markRootsForRefresh(): List<VirtualFile> = emptyList()
}
