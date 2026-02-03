// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation
import com.intellij.openapi.vcs.VcsConfiguration.StandardOption
import com.intellij.openapi.vcs.VcsConsoleLine
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vcs.VcsRootSettings
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.VcsShowSettingOption
import com.intellij.openapi.vcs.VirtualFileFilter
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.checkout.CompositeCheckoutListener
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.history.VcsHistoryCache
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses
import com.intellij.openapi.vcs.impl.projectlevelman.MappingsToRoots
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.openapi.vcs.impl.projectlevelman.OptionsAndConfirmations
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowConfirmationOption
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowSettingOption
import com.intellij.openapi.vcs.update.ActionInfo
import com.intellij.openapi.vcs.update.UpdateInfoTree
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.ui.content.ContentManager
import com.intellij.util.ContentUtilEx
import com.intellij.util.Processor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.console.VcsConsoleTabService
import com.intellij.vcsUtil.VcsImplUtil
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@State(name = "VcsDirectoryMappings", storages = [Storage("vcs.xml")])
class ProjectLevelVcsManagerImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : ProjectLevelVcsManagerEx(), PersistentStateComponent<Element>, Disposable {
  private val mappingsHolder = NewMappings(project, this, coroutineScope)
  private var mappingsLoaded = false

  private val backgroundOperationCounter = AtomicInteger()
  private val backgroundRunningTasks = ConcurrentCollectionFactory.createConcurrentSet<ActionKey>()

  override val annotationLocalChangesListener: VcsAnnotationLocalChangesListener
    get() = project.service<VcsAnnotationLocalChangesListener>()

  override val vcsHistoryCache: VcsHistoryCache
    get() = VcsCacheManager.getInstance(project).vcsHistoryCache

  override val contentRevisionCache: ContentRevisionCache
    get() = VcsCacheManager.getInstance(project).contentRevisionCache

  override fun dispose() {
    Disposer.dispose(mappingsHolder)
  }

  override fun findVcsByName(name: String?): AbstractVcs? {
    if (name == null) return null
    val vcs = AllVcses.getInstance(project).getByName(name)
    if (vcs == null && project.isDisposed()) {
      // Take readLock to avoid race between Project.isDisposed and Disposer.dispose.
      ReadAction.run(ThrowableRunnable { ProgressManager.checkCanceled() })
    }
    return vcs
  }

  override fun getAllVcss(): Array<VcsDescriptor> = AllVcses.getInstance(project).getAll()

  override fun getAllSupportedVcss(): Array<AbstractVcs> = AllVcses.getInstance(project).getSupportedVcses()

  fun haveVcses(): Boolean = !AllVcses.getInstance(project).isEmpty()

  override fun checkAllFilesAreUnder(abstractVcs: AbstractVcs, files: Array<VirtualFile>): Boolean =
    files.all { getVcsFor(it) === abstractVcs }

  @NlsSafe
  override fun getShortNameForVcsRoot(file: VirtualFile): @NlsSafe String =
    mappingsHolder.getShortNameFor(file)
    ?: project.getBaseDir()?.let { VfsUtilCore.getRelativePath(file, it, File.separatorChar) }?.ifEmpty { file.getName() }
    ?: file.presentableName

  override fun getVcsFor(file: VirtualFile?): AbstractVcs? {
    if (project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.vcs
  }

  override fun getVcsFor(file: FilePath?): AbstractVcs? {
    if (project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.vcs
  }

  override fun getVcsRootFor(file: VirtualFile?): VirtualFile? {
    if (file == null || project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.root
  }

  override fun getVcsRootObjectFor(file: VirtualFile?): VcsRoot? {
    if (file == null || project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.let { VcsRoot(it.vcs, it.root) }
  }

  override fun getVcsRootFor(file: FilePath?): VirtualFile? {
    if (file == null || project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.root
  }

  override fun getVcsRootObjectFor(file: FilePath?): VcsRoot? {
    if (file == null || project.isDisposed()) return null

    val root = mappingsHolder.getMappedRootFor(file)
    return if (root != null) VcsRoot(root.vcs, root.root) else null
  }

  @get:ApiStatus.Internal
  val vcsRootObjectsForDefaultMapping: List<VcsRoot>
    get() =
      mappingsHolder.allMappedRoots.filter { root ->
        val vcs = root.vcs
        root.mapping.isDefaultMapping && vcs != null && vcs.customConvertor == null
      }.map {
        VcsRoot(it.vcs, it.root)
      }

  @TestOnly
  fun unregisterVcs(vcs: AbstractVcs) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && mappingsHolder.haveActiveVcs(vcs.name)) {
      // unlikely
      LOG.warn("Active vcs '${vcs.name}' is being unregistered. Remove from mappings first.")
    }
    mappingsHolder.beingUnregistered(vcs.name)
    AllVcses.getInstance(project).unregisterManually(vcs)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override val contentManager: ContentManager?
    get() = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)?.getContentManager()

  override fun checkVcsIsActive(vcs: AbstractVcs): Boolean = checkVcsIsActive(vcs.name)

  override fun checkVcsIsActive(vcsName: String?): Boolean = mappingsHolder.haveActiveVcs(vcsName)

  override fun getAllActiveVcss(): Array<AbstractVcs> = mappingsHolder.activeVcses

  override fun getSingleVCS(): AbstractVcs? = getAllActiveVcss().singleOrNull()

  override fun hasActiveVcss(): Boolean = mappingsHolder.hasActiveVcss()

  override fun areVcsesActivated(): Boolean = mappingsHolder.isActivated

  override fun hasAnyMappings(): Boolean = !mappingsHolder.isEmpty

  override fun getDirectoryMappings(): MutableList<VcsDirectoryMapping> {
    return mappingsHolder.directoryMappings
  }

  override fun getDirectoryMappings(vcs: AbstractVcs): List<VcsDirectoryMapping> {
    return mappingsHolder.getDirectoryMappings(vcs.name)
  }

  override fun getDirectoryMappingFor(path: FilePath): VcsDirectoryMapping? {
    if (project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(path)?.mapping
  }

  private fun getDirectoryMappingFor(file: VirtualFile): VcsDirectoryMapping? {
    if (project.isDisposed()) return null
    return mappingsHolder.getMappedRootFor(file)?.mapping
  }

  override fun setDirectoryMapping(path: @NonNls String, activeVcsName: @NonNls String?) {
    if (mappingsLoaded) {
      // ignore per-module VCS settings if the mapping table was loaded from .ipr
      return
    }
    mappingsHolder.setMapping(FileUtil.toSystemIndependentName(path), activeVcsName)
  }

  fun registerNewDirectMappings(detectedRoots: Collection<Pair<VirtualFile, AbstractVcs>>) {
    val mappings = mappingsHolder.directoryMappings.toMutableList()
    val knownMappedRoots = mappings.mapTo(mutableSetOf()) { it.directory }

    val newMappings = detectedRoots.asSequence().map { (file, vcs) ->
      VcsDirectoryMapping(file.getPath(), vcs.name)
    }.filter {
      !knownMappedRoots.contains(it.directory)
    }
    mappings.addAll(newMappings)
    setAutoDirectoryMappings(mappings)
  }

  fun setAutoDirectoryMappings(mappings: List<VcsDirectoryMapping?>) {
    mappingsHolder.setDirectoryMappings(mappings)
    mappingsHolder.cleanupMappings()
  }

  fun removeDirectoryMapping(mapping: VcsDirectoryMapping) {
    mappingsHolder.removeDirectoryMapping(mapping)
  }

  override fun setDirectoryMappings(mappings: List<VcsDirectoryMapping>) {
    mappingsHolder.setDirectoryMappings(mappings)
  }

  override fun scheduleMappedRootsUpdate() {
    mappingsHolder.scheduleMappedRootsUpdate()
  }

  fun updateMappedVcsesImmediately() {
    mappingsHolder.updateMappedVcsesImmediately()
  }

  override fun iterateVcsRoot(root: VirtualFile, iterator: Processor<in FilePath>) {
    VcsRootIterator.iterateVcsRoot(project, root, iterator)
  }

  override fun iterateVcsRoot(root: VirtualFile, iterator: Processor<in FilePath>, directoryFilter: VirtualFileFilter?) {
    VcsRootIterator.iterateVcsRoot(project, root, iterator, directoryFilter)
  }

  override fun iterateVfUnderVcsRoot(file: VirtualFile, processor: Processor<in VirtualFile>) {
    VcsRootIterator.iterateVfUnderVcsRoot(project, file, processor)
  }

  override fun getStandardOption(option: StandardOption, vcs: AbstractVcs): VcsShowSettingOption {
    val options = getOptions(option)
    options.addApplicableVcs(vcs)
    return options
  }

  override fun getStandardConfirmation(
    option: StandardConfirmation,
    vcs: AbstractVcs?,
  ): VcsShowConfirmationOption {
    val result = getConfirmation(option)
    if (vcs != null) {
      result.addApplicableVcs(vcs)
    }
    return result
  }

  private val optionsAndConfirmations: OptionsAndConfirmations
    get() = OptionsAndConfirmationsHolder.getInstance(project).optionsAndConfirmations

  override val allConfirmations: List<PersistentVcsShowConfirmationOption>
    get() = optionsAndConfirmations.allConfirmations

  override fun getConfirmation(option: StandardConfirmation): PersistentVcsShowConfirmationOption =
    optionsAndConfirmations.getConfirmation(option)

  override fun getOptions(option: StandardOption): PersistentVcsShowSettingOption =
    optionsAndConfirmations.getOption(option)

  override val allOptions: List<PersistentVcsShowSettingOption>
    get() = optionsAndConfirmations.allOptions

  override val isBackgroundVcsOperationRunning: Boolean
    get() = backgroundOperationCounter.get() > 0

  override fun startBackgroundVcsOperation() {
    backgroundOperationCounter.incrementAndGet()
  }

  override fun stopBackgroundVcsOperation() {
    // in fact, the condition is "should not be called under ApplicationManager.invokeLater() and similar"
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val counter = backgroundOperationCounter.getAndDecrement()
    LOG.assertTrue(counter > 0, "myBackgroundOperationCounter was $counter while should have been > 0")
  }

  override fun getRootsUnderVcsWithoutFiltering(vcs: AbstractVcs): List<VirtualFile> = mappingsHolder.getMappingsAsFilesUnderVcs(vcs)

  override fun getRootsUnderVcs(vcs: AbstractVcs): Array<VirtualFile> = MappingsToRoots.getRootsUnderVcs(project, mappingsHolder, vcs)

  override fun getAllVersionedRoots(): Array<VirtualFile> {
    val vFiles = ArrayList<VirtualFile>()
    val vcses = mappingsHolder.activeVcses
    for (vcs in vcses) {
      Collections.addAll(vFiles, *getRootsUnderVcs(vcs))
    }

    return VfsUtilCore.toVirtualFileArray(vFiles)
  }

  override fun getAllVcsRoots(): Array<VcsRoot> {
    val vcsRoots = ArrayList<VcsRoot>()
    val vcses = mappingsHolder.activeVcses
    for (vcs in vcses) {
      val roots = getRootsUnderVcs(vcs)
      for (root in roots) {
        vcsRoots.add(VcsRoot(vcs, root))
      }
    }
    return vcsRoots.toTypedArray<VcsRoot>()
  }

  override fun getConsolidatedVcsName(): String {
    val singleVcs = getSingleVCS()
    return singleVcs?.shortNameWithMnemonic ?: VcsBundle.message("vcs.generic.name.with.mnemonic")
  }

  @Deprecated("A plugin should not need to call this.")
  override fun notifyDirectoryMappingChanged() {
    fireDirectoryMappingsChanged()
  }

  override fun loadState(element: Element) {
    val mappingsList: MutableList<VcsDirectoryMapping?> = ArrayList<VcsDirectoryMapping?>()
    for (child in element.getChildren(ELEMENT_MAPPING)) {
      val vcsName = child.getAttributeValue(ATTRIBUTE_VCS)
      val directory = child.getAttributeValue(ATTRIBUTE_DIRECTORY)
      if (directory == null) continue

      var rootSettings: VcsRootSettings? = null
      val rootSettingsElement = child.getChild(ELEMENT_ROOT_SETTINGS)
      if (rootSettingsElement != null) {
        val className = rootSettingsElement.getAttributeValue(ATTRIBUTE_CLASS)
        val vcsInstance = if (vcsName == null) null else AllVcses.getInstance(project).getByName(vcsName)
        if (vcsInstance != null && className != null) {
          rootSettings = vcsInstance.createEmptyVcsRootSettings()
          if (rootSettings != null) {
            try {
              rootSettings.readExternal(rootSettingsElement)
            }
            catch (e: InvalidDataException) {
              LOG.error("Failed to load VCS root settings class " + className + " for VCS " + vcsInstance.javaClass.getName(), e)
            }
          }
        }
      }

      val mapping = VcsDirectoryMapping(directory, vcsName, rootSettings)
      mappingsList.add(mapping)

      mappingsLoaded = mappingsLoaded or !mapping.isDefaultMapping
    }
    mappingsHolder.setDirectoryMappingsFromConfig(mappingsList)
  }

  override fun getState(): Element {
    val element = Element("state")
    for (mapping in getDirectoryMappings()) {
      val rootSettings = mapping.rootSettings
      if (rootSettings == null && mapping.isDefaultMapping && mapping.isNoneMapping) {
        continue
      }

      val child = Element(ELEMENT_MAPPING)
      child.setAttribute(ATTRIBUTE_DIRECTORY, mapping.directory)
      child.setAttribute(ATTRIBUTE_VCS, mapping.vcs)
      if (rootSettings != null) {
        val rootSettingsElement = Element(ELEMENT_ROOT_SETTINGS)
        rootSettingsElement.setAttribute(ATTRIBUTE_CLASS, rootSettings.javaClass.getName())
        try {
          rootSettings.writeExternal(rootSettingsElement)
          child.addContent(rootSettingsElement)
        }
        catch (e: WriteExternalException) {
          // don't add element
        }
      }
      element.addContent(child)
    }
    return element
  }

  /**
   * Used to guess VCS for automatic mapping through a look into a working copy
   */
  override fun findVersioningVcs(file: VirtualFile): AbstractVcs? {
    val checkedVcses = HashSet<String>()

    for (checker in VcsRootChecker.EXTENSION_POINT_NAME.extensionList) {
      val vcsName = checker.getSupportedVcs().name
      checkedVcses.add(vcsName)

      if (checker.isRoot(file)) {
        return findVcsByName(vcsName)
      }
    }

    var foundVcs: String? = null
    for (vcsDescriptor in getAllVcss()) {
      val vcsName = vcsDescriptor.name
      if (checkedVcses.contains(vcsName)) continue

      if (vcsDescriptor.probablyUnderVcs(file)) {
        if (foundVcs != null) {
          return null
        }
        foundVcs = vcsName
      }
    }
    return findVcsByName(foundVcs)
  }

  override fun getRootChecker(vcs: AbstractVcs): VcsRootChecker {
    for (checker in VcsRootChecker.EXTENSION_POINT_NAME.getIterable()) {
      if (checker == null) break
      if (checker.getSupportedVcs() == vcs.keyInstanceMethod) {
        return checker
      }
    }
    return DefaultVcsRootChecker(vcs, getDescriptor(vcs.name))
  }

  private fun getDescriptor(vcsName: String): VcsDescriptor? {
    if (project.isDisposed()) return null
    return AllVcses.getInstance(project).getDescriptor(vcsName)
  }

  override val compositeCheckoutListener: CheckoutProvider.Listener
    get() = CompositeCheckoutListener(project)

  @Suppress("OVERRIDE_DEPRECATION")
  override fun fireDirectoryMappingsChanged() {
    if (project.isOpen() && !project.isDisposed()) {
      mappingsHolder.notifyMappingsChanged()
    }
  }

  /**
   * @return VCS name for default mapping, if any
   */
  override fun haveDefaultMapping(): String? = mappingsHolder.haveDefaultMapping()

  @CalledInAny
  fun isBackgroundTaskRunning(vararg keys: Any): Boolean = backgroundRunningTasks.contains(ActionKey(*keys))

  @RequiresEdt
  fun startBackgroundTask(vararg keys: Any) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.assertTrue(backgroundRunningTasks.add(ActionKey(*keys)))
  }

  @RequiresEdt
  fun stopBackgroundTask(vararg keys: Any) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.assertTrue(backgroundRunningTasks.remove(ActionKey(*keys)))
  }

  /**
   * @see com.intellij.openapi.vcs.ProjectLevelVcsManager.runAfterInitialization
   * @see VcsStartupActivity
   */
  fun addInitializationRequest(vcsInitObject: VcsInitObject, runnable: Runnable) {
    VcsInitialization.getInstance(project).add(vcsInitObject, runnable)
  }

  override fun runAfterInitialization(runnable: Runnable) {
    addInitializationRequest(VcsInitObject.AFTER_COMMON, runnable)
  }

  override suspend fun awaitInitialization() {
    project.serviceAsync<VcsInitialization>().await()
  }

  override fun isFileInContent(vf: VirtualFile?): Boolean {
    if (vf == null) return false
    return ReadAction.compute(ThrowableComputable {
      if (!vf.isValid()) return@ThrowableComputable false
      val fileIndex = FileIndexFacade.getInstance(project)
      val isUnderProject = isFileInBaseDir(vf) ||
                           isInDirectoryBasedRoot(vf) ||
                           hasExplicitMapping(vf) ||
                           fileIndex.isInContent(vf) ||
                           (!Registry.`is`("ide.hide.excluded.files") && fileIndex.isExcludedFile(vf))
      isUnderProject && !isIgnored(vf)
    })
  }

  override fun isIgnored(vf: VirtualFile): Boolean {
    return ReadAction.compute(ThrowableComputable {
      if (project.isDisposed() || project.isDefault) return@ThrowableComputable false
      if (!vf.isValid()) return@ThrowableComputable false
      if (Registry.`is`("ide.hide.excluded.files")) {
        return@ThrowableComputable FileIndexFacade.getInstance(project).isExcludedFile(vf)
      }
      else {
        return@ThrowableComputable FileIndexFacade.getInstance(project).isUnderIgnored(vf)
      }
    })
  }

  override fun isIgnored(filePath: FilePath): Boolean {
    return ReadAction.compute(ThrowableComputable {
      if (project.isDisposed() || project.isDefault) return@ThrowableComputable false
      if (Registry.`is`("ide.hide.excluded.files")) {
        val vf = VcsImplUtil.findValidParentAccurately(filePath)
        return@ThrowableComputable vf != null && FileIndexFacade.getInstance(project).isExcludedFile(vf)
      }
      else {
        // WARN: might differ from 'myExcludedIndex.isUnderIgnored' if whole content root is under folder with 'ignored' name.
        val fileTypeManager = FileTypeManager.getInstance()
        for (name in StringUtil.tokenize(filePath.getPath(), "/")) {
          if (fileTypeManager.isFileIgnored(name)) {
            return@ThrowableComputable true
          }
        }
        return@ThrowableComputable false
      }
    })
  }

  private fun isInDirectoryBasedRoot(file: VirtualFile): Boolean {
    if (project.isDirectoryBased) {
      return project.stateStore.isProjectFile(file)
    }
    return false
  }

  private fun isFileInBaseDir(file: VirtualFile): Boolean {
    val baseDir = project.getBaseDir() ?: return false

    if (file.isDirectory()) {
      return baseDir == file
    }
    else {
      return baseDir == file.getParent()
    }
  }

  private fun hasExplicitMapping(vFile: VirtualFile): Boolean {
    val mapping = getDirectoryMappingFor(vFile)
    return mapping != null && !mapping.isDefaultMapping
  }

  @TestOnly
  fun waitForInitialized() {
    VcsInitialization.getInstance(project).waitFinished()
  }

  @Deprecated("Use {@link com.intellij.vcs.console.VcsConsoleTabService}")
  override fun showConsole(then: Runnable?) {
    VcsConsoleTabService.getInstance(project).showConsoleTab(true, null)
  }

  @Deprecated("Use {@link com.intellij.vcs.console.VcsConsoleTabService}")
  override fun scrollConsoleToTheEnd() {
    VcsConsoleTabService.getInstance(project).showConsoleTabAndScrollToTheEnd()
  }

  @Deprecated("")
  override fun addMessageToConsoleWindow(message: String?, attributes: TextAttributes?) {
    addMessageToConsoleWindow(message, ConsoleViewContentType("", attributes))
  }

  override fun addMessageToConsoleWindow(message: String?, contentType: ConsoleViewContentType) {
    VcsConsoleTabService.getInstance(project).addMessage(message, contentType)
  }

  override fun addMessageToConsoleWindow(line: VcsConsoleLine?) {
    VcsConsoleTabService.getInstance(project).addMessage(line)
  }

  @RequiresEdt
  override fun showUpdateProjectInfo(
    updatedFiles: UpdatedFiles?,
    displayActionName: String?,
    actionInfo: ActionInfo?,
    canceled: Boolean,
  ): UpdateInfoTree? {
    if (!project.isOpen() || project.isDisposed()) return null
    val contentManager = contentManager ?: return null // content manager is made null during dispose; flag is set later
    val updateInfoTree = UpdateInfoTree(contentManager, project, updatedFiles, displayActionName, actionInfo)
    val tabName = DateFormatUtil.formatDateTime(System.currentTimeMillis())
    ContentUtilEx.addTabbedContent(contentManager, updateInfoTree, "Update Info",
                                   VcsBundle.messagePointer("vcs.update.tab.name"), Supplier { tabName },
                                   false, updateInfoTree)
    updateInfoTree.expandRootChildren()
    return updateInfoTree
  }

  private class ActionKey(private vararg val objects: Any) {
    override fun equals(other: Any?): Boolean {
      if (other == null || javaClass != other.javaClass) return false
      return objects.contentEquals((other as ActionKey).objects)
    }

    override fun hashCode(): Int {
      return objects.contentHashCode()
    }

    override fun toString(): String {
      return javaClass.toString() + " - " + objects.contentToString()
    }
  }

  @TestOnly
  fun registerVcs(vcs: AbstractVcs) {
    AllVcses.getInstance(project).registerManually(vcs)
  }

  internal class ActivateVcsesStartupActivity : VcsStartupActivity {
    override fun runActivity(project: Project) {
      if (WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
        return
      }

      getInstanceImpl(project).mappingsHolder.activateActiveVcses()
    }

    override val order: Int
      get() = VcsInitObject.MAPPINGS.order
  }

  internal class TrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
      getInstanceImpl(project).updateMappedVcsesImmediately()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ProjectLevelVcsManagerImpl::class.java)

    private const val ELEMENT_MAPPING: @NonNls String = "mapping"
    private const val ATTRIBUTE_DIRECTORY: @NonNls String = "directory"
    private const val ATTRIBUTE_VCS: @NonNls String = "vcs"
    private const val ELEMENT_ROOT_SETTINGS: @NonNls String = "rootSettings"
    private const val ATTRIBUTE_CLASS: @NonNls String = "class"

    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectLevelVcsManagerImpl = getInstance(project) as ProjectLevelVcsManagerImpl
  }
}
