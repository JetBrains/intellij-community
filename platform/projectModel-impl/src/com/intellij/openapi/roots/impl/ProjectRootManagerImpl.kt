// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.WsmProjectSettingsEntityUtils
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ProjectRootManagerImpl>()

private val EP_NAME = ProjectExtensionPointName<ProjectExtension>("com.intellij.projectExtension")

@State(name = "ProjectRootManager")
@ApiStatus.Internal
open class ProjectRootManagerImpl(
  @JvmField val project: Project,
  @JvmField protected val coroutineScope: CoroutineScope,
) : ProjectRootManagerEx() {

  private val projectJdkEventDispatcher = EventDispatcher.create(ProjectJdkListener::class.java)
  private val moduleRootManagerInstances = ConcurrentHashMap<Module, ModuleRootManager>()

  private val rootCache: OrderRootsCache

  init {
    @Suppress("LeakingThis")
    rootCache = getOrderRootsCache(project)
    project.getMessageBus().simpleConnect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        val currentName = projectSdkName
        if (previousName == currentName) {
          val sdkId = SdkId(jdk.getName(), jdk.getSdkType().getName())
          // if already had jdk name and that name was the name of the jdk just changed
          project.workspaceModel.updateProjectModel("jdkNameChanged: $sdkId") { mutableStorage ->
            WsmProjectSettingsEntityUtils.addOrModifyProjectSettingsEntity(project, mutableStorage) { entity ->
              entity.projectSdk = sdkId
            }
          }
        }
      }
    })
    project.messageBus.simpleConnect().subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        moduleRootManagerInstances.remove(module)
      }
    })
  }

  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectRootManagerImpl = getInstance(project) as ProjectRootManagerImpl

    @ApiStatus.Internal
    fun extractLocalPath(url: String): String {
      val path = URLUtil.extractPath(url)
      val separatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR)
      return if (separatorIndex > 0) path.take(separatorIndex) else path
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
          ApplicationManager.getApplication().runWriteAction { fireRootsChanged(copy(changes!!)) }
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
        if (fireRootsChanged(copy(changes!!)) && pendingRootsChanged == 0) {
          isChanged = false
          changes = null
        }
      }
    }

    @ApiStatus.Internal
    protected abstract fun fireRootsChanged(change: ChangeList): Boolean

    @ApiStatus.Internal
    protected abstract fun initiateChangelist(change: Change): ChangeList

    @ApiStatus.Internal
    protected abstract fun accumulate(current: ChangeList, change: Change): ChangeList

    @ApiStatus.Internal
    protected abstract fun copy(changes: ChangeList): ChangeList

    @get:ApiStatus.Internal
    protected abstract val genericChange: Change
  }

  @get:ApiStatus.Internal
  val rootsChanged: BatchSession<RootsChangeRescanningInfo, MutableList<RootsChangeRescanningInfo>> = object : BatchSession<RootsChangeRescanningInfo, MutableList<RootsChangeRescanningInfo>>(false) {
    override fun fireRootsChanged(change: MutableList<RootsChangeRescanningInfo>): Boolean {
      return this@ProjectRootManagerImpl.fireRootsChanged(fileTypes = false, indexingInfos = change)
    }

    override fun accumulate(
      current: MutableList<RootsChangeRescanningInfo>,
      change: RootsChangeRescanningInfo,
    ): MutableList<RootsChangeRescanningInfo> {
      current.add(change)
      return current
    }

    override val genericChange: RootsChangeRescanningInfo
      get() = RootsChangeRescanningInfo.TOTAL_RESCAN

    override fun initiateChangelist(change: RootsChangeRescanningInfo) = SmartList(change)

    override fun copy(changes: MutableList<RootsChangeRescanningInfo>): MutableList<RootsChangeRescanningInfo> {
      return ArrayList(changes)
    }
  }

  @ApiStatus.Internal
  @JvmField val fileTypesChanged: BatchSession<Boolean, Boolean> = object : BatchSession<Boolean, Boolean>(true) {
    override fun fireRootsChanged(change: Boolean): Boolean {
      return this@ProjectRootManagerImpl.fireRootsChanged(true, emptyList())
    }

    override fun accumulate(current: Boolean, change: Boolean): Boolean {
      return current || change
    }

    override val genericChange: Boolean
      get() = true

    override fun initiateChangelist(change: Boolean): Boolean = change

    override fun copy(changes: Boolean): Boolean = changes
  }

  @ApiStatus.Internal
  open val rootsValidityChangedListener: VirtualFilePointerListener = object : VirtualFilePointerListener {}

  final override fun getFileIndex(): ProjectFileIndex {
    return ProjectFileIndex.getInstance(project)
  }

  @ApiStatus.Internal
  final override fun getContentRootUrls(): List<String> {
    val modules = moduleManager.modules
    val result = ArrayList<String>(modules.size)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getContentRootUrls())
    }
    return result
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  override fun getContentSourceRoots(): Array<VirtualFile> {
    val modules = moduleManager.modules
    val result = ArrayList<VirtualFile>(modules.size)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getSourceRoots())
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  @ApiStatus.Internal
  override fun getModuleSourceRoots(rootTypes: Set<JpsModuleSourceRootType<*>?>): List<VirtualFile> {
    val modules = moduleManager.modules
    val roots = ArrayList<VirtualFile>(modules.size)
    for (module in modules) {
      roots.addAll(ModuleRootManager.getInstance(module).getSourceRoots(rootTypes))
    }
    return roots
  }

  @ApiStatus.Internal
  override fun orderEntries(): OrderEnumerator = ProjectOrderEnumerator(project, rootCache)

  @ApiStatus.Internal
  override fun orderEntries(modules: Collection<Module>): OrderEnumerator = ModulesOrderEnumerator(project, modules)

  @ApiStatus.Internal
  override fun getContentRootsFromAllModules(): Array<VirtualFile> {
    val modules = moduleManager.sortedModules
    val result = ArrayList<VirtualFile>(modules.size + 1)
    for (module in modules) {
      result.addAll(ModuleRootManager.getInstance(module).getContentRoots())
    }
    @Suppress("DEPRECATION")
    project.baseDir?.let {
      result.add(it)
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  @ApiStatus.Internal
  final override fun getProjectSdk(): Sdk? {
    val sdkName = projectSdkName
    if (sdkName == null) {
      return null
    }

    val projectJdkTable = ProjectJdkTable.getInstance(project)
    val sdkType = projectSdkTypeName
    return if (sdkType == null) {
      projectJdkTable.findJdk(sdkName)
    } else {
      projectJdkTable.findJdk(sdkName, sdkType)
    }
  }

  @ApiStatus.Internal
  override fun getProjectSdkName(): String? {
    val settings = WsmSingletonEntityUtils.getSingleEntity(project.workspaceModel.currentSnapshot, ProjectSettingsEntity::class.java)
    return settings?.projectSdk?.name
  }

  @ApiStatus.Internal
  override fun getProjectSdkTypeName(): String? {
    val settings = WsmSingletonEntityUtils.getSingleEntity(project.workspaceModel.currentSnapshot, ProjectSettingsEntity::class.java)
    return settings?.projectSdk?.type
  }

  @ApiStatus.Internal
  @RequiresWriteLock(generateAssertion = false)
  override fun setProjectSdk(sdk: Sdk?) {
    if (sdk == null) {
      setOrClearProjectSdkName(null, null)
    }
    else {
      setOrClearProjectSdkName(sdk.getName(), sdk.getSdkType().getName())
    }
  }

  fun projectJdkChanged() {
    incModificationCount()
    // There is no mergeRootsChangesDuring because currently it has a bug: "after" event will never fire if mergeRootsChangesDuring
    // is invoked while another rootsChange event (caused by the WSM change) is in progress (see RootsChangedTest).
    actionToRunWhenProjectJdkChanges.run()
    fireJdkChanged()
  }

  private fun fireJdkChanged() {
    val sdk = getProjectSdk()
    for (extension in EP_NAME.getExtensions(project)) {
      extension.projectSdkChanged(sdk)
    }
  }

  @get:ApiStatus.Internal
  protected open val actionToRunWhenProjectJdkChanges: Runnable
    get() = Runnable { projectJdkEventDispatcher.getMulticaster().projectJdkChanged() }

  @ApiStatus.Internal
  @RequiresWriteLock(generateAssertion = false)
  override fun setProjectSdkName(name: String, sdkTypeName: String) {
    setOrClearProjectSdkName(name, sdkTypeName)
  }

  private fun setOrClearProjectSdkName(name: String?, sdkTypeName: String?) {
    ThreadingAssertions.assertWriteAccess()
    val newSdk = if (name != null && sdkTypeName != null) SdkId(name, sdkTypeName) else null
    project.workspaceModel.updateProjectModel("setOrClearProjectSdkName: $newSdk") { mutableStorage ->
      WsmProjectSettingsEntityUtils.addOrModifyProjectSettingsEntity(project, mutableStorage) { entity ->
        entity.projectSdk = newSdk
      }
    }
  }

  override fun addProjectJdkListener(listener: ProjectJdkListener) {
    projectJdkEventDispatcher.addListener(listener)
  }

  override fun removeProjectJdkListener(listener: ProjectJdkListener) {
    projectJdkEventDispatcher.removeListener(listener)
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  protected open fun clearScopesCaches() {
    clearScopesCachesForModules()
  }

  @ApiStatus.Internal
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

  override fun getModuleRootManager(module: Module): ModuleRootManager {
    return moduleRootManagerInstances.computeIfAbsent(module) { ModuleRootComponentBridge(module) }
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  override fun markRootsForRefresh(): List<VirtualFile> = emptyList()
}
