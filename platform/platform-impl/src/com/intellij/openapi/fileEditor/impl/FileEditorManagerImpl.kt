// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION", "ReplaceGetOrSet", "LeakingThis", "ReplaceJavaStaticMethodWithKotlinAnalog")
@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.SplitAction
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.LangBundle
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.Companion.SINGLETON_EDITOR_IN_WINDOW
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.TEXT_EDITOR_PROVIDER_TYPE_ID
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.impl.pumpEventsForHierarchy
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.zipWithNext
import com.intellij.pom.Navigatable
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.impl.DockManagerImpl
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ExceptionUtil
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.containers.toArray
import com.intellij.util.messages.impl.MessageListenerList
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<FileEditorManagerImpl>()

@Internal
@OptIn(ExperimentalCoroutinesApi::class)
@State(name = "FileEditorManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
open class FileEditorManagerImpl(
  private val project: Project,
  @JvmField protected val coroutineScope: CoroutineScope,
) : FileEditorManagerEx(), PersistentStateComponent<Element>, Disposable {
  private val dumbModeFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  @Internal
  enum class OpenMode {
    NEW_WINDOW, RIGHT_SPLIT, DEFAULT
  }

  // temporarily used during initialization
  private val state = AtomicReference<EditorSplitterState?>()

  lateinit var mainSplitters: EditorsSplitters
    private set

  @JvmField
  internal val initJob: Job

  private val dockable = lazy {
    DockableEditorTabbedContainer(
      splitters = mainSplitters,
      disposeWhenEmpty = false,
      coroutineScope = coroutineScope.childScope("DockableEditorTabbedContainer"),
    )
  }

  private val selectionHistory = SelectionHistory()

  private val fileUpdateChannel: MergingUpdateChannel<VirtualFile> = MergingUpdateChannel(delay = 50.milliseconds) { toUpdate ->
    if (toUpdate.isEmpty()) {
      return@MergingUpdateChannel
    }

    val fileDocumentManager = serviceAsync<FileDocumentManager>()
    val allSplitters = getAllSplitters()
    for (file in toUpdate) {
      if (fileDocumentManager.isFileModified(file)) {
        for (composite in openedComposites) {
          if (composite.file == file) {
            composite.isPreview = false
          }
        }
      }

      for (splitters in allSplitters) {
        splitters.updateFileIcon(file)
        splitters.updateFileColor(file = file)
        splitters.updateFileBackgroundColor(file)
        // https://youtrack.jetbrains.com/issue/IJPL-157805/Diff-the-window-title-doesnt-update-the-filename-on-selecting-navigating-to-another-file
        if (splitters !== mainSplitters) {
          splitters.updateFrameTitle()
        }
      }
    }
  }

  private val fileTitleUpdateChannel: MergingUpdateChannel<VirtualFile?> = MergingUpdateChannel(delay = 50.milliseconds) { toUpdate ->
    val allSplitters = getAllSplitters()
    for (file in toUpdate) {
      updateFileNames(allSplitters = allSplitters, file = file)
    }
  }

  /**
   * Removes invalid editor and updates "modified" status.
   */
  @JvmField
  internal val editorPropertyChangeListener: PropertyChangeListener = MyEditorPropertyChangeListener()

  private data class EditorCompositeEntry(
    @JvmField val composite: EditorComposite,
    // non-volatile - that's ok
    @JvmField var delayedState: FileEntry?,
  )

  private var contentFactory: DockableEditorContainerFactory? = null

  private val openedCompositeEntries = CopyOnWriteArrayList<EditorCompositeEntry>()

  private val openedComposites: Sequence<EditorComposite>
    get() = openedCompositeEntries.asSequence().map { it.composite }

  private val listenerList = MessageListenerList(project.messageBus, FileEditorManagerListener.FILE_EDITOR_MANAGER)

  private val splitterFlow = MutableSharedFlow<EditorsSplitters>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  final override val currentFileEditorFlow: StateFlow<FileEditor?>

  override val dockContainer: DockContainer?
    get() = dockable.value

  private val creationStack = if (ApplicationManager.getApplication().isUnitTestMode) ExceptionUtil.currentStackTrace() else null

  init {
    @Suppress("TestOnlyProblems")
    if (project is ProjectEx && project.isLight && ALLOW_IN_LIGHT_PROJECT.get(project) != true) {
      throw IllegalStateException("Using of FileEditorManagerImpl is forbidden for a light test. Creation stack: $creationStack")
    }

    coroutineScope.launch {
      splitterFlow
        .flatMapLatest { it.currentWindowFlow }
        .flatMapLatest { editorWindow ->
          if (editorWindow == null) {
            return@flatMapLatest emptyFlow()
          }

          editorWindow.currentCompositeFlow.filterNotNull().map { it.file to editorWindow }
        }
        .collect { item ->
          // addRecord not in EDT and do not depend on file editor creation
          selectionHistory.addRecord(item)
        }
    }

    val selectionFlow: StateFlow<SelectionState?> = splitterFlow
      .flatMapLatest { it.currentCompositeFlow }
      .flatMapLatest { composite ->
        if (composite == null) {
          return@flatMapLatest flowOf(null)
        }

        composite.selectedEditorWithProvider.mapLatest { fileEditorWithProvider ->
          fileEditorWithProvider?.let {
            SelectionState(composite = composite, fileEditorProvider = it)
          }
        }
      }
      .stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = null)

    coroutineScope.launch {
      // not using collectLatest() to ensure that the listeners miss no selection update
      selectionFlow
        .zipWithNext { oldState, state ->
          val oldEditorWithProvider = oldState?.fileEditorProvider
          val newEditorWithProvider = state?.fileEditorProvider
          if (oldEditorWithProvider == newEditorWithProvider) {
            return@zipWithNext
          }

          val publisher = project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
          // expected in EDT
          withContext(Dispatchers.EDT) {
            runCatching {
              fireSelectionChanged(
                newState = state,
                oldEditorWithProvider = oldEditorWithProvider,
                newEditorWithProvider = newEditorWithProvider,
                publisher = publisher,
              )
            }.getOrLogException(LOG)
          }
        }
        .collect()
    }

    currentFileEditorFlow = selectionFlow
      .map { it?.fileEditorProvider?.fileEditor }
      .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    coroutineScope.launch {
      val providerManager = serviceAsync<FileEditorProviderManager>()
      dumbModeFinished.collectLatest {
        dumbModeFinished(project = project, fileEditorProviderManager = providerManager)
      }
    }

    closeFilesOnFileEditorRemoval()

    if (ApplicationManager.getApplication().isUnitTestMode || forceUseUiInHeadlessMode()) {
      initJob = CompletableDeferred(value = Unit)
      mainSplitters = EditorsSplitters(manager = this, coroutineScope = coroutineScope)
      check(splitterFlow.tryEmit(mainSplitters))

      project.messageBus.connect(coroutineScope).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
        override fun exitDumbMode() {
          // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
          dumbModeFinished.tryEmit(Unit)
        }
      })
    }
    else {
      initJob = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val component = EditorsSplitters(manager = this@FileEditorManagerImpl, coroutineScope = coroutineScope)
        component.isFocusable = false
        // prepare for toolwindow manager
        mainSplitters = component
        check(splitterFlow.tryEmit(component))

        // connect after we set mainSplitters
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          coroutineScope.launch {
            postInit()
          }
        }
      }
    }
  }

  private fun postInit() {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(FileStatusListener.TOPIC, MyFileStatusListener())
    connection.subscribe(FileTypeManager.TOPIC, MyFileTypeListener())
    if (!LightEdit.owns(project)) {
      val rootListener = MyRootListener(this)
      connection.subscribe(ModuleRootListener.TOPIC, rootListener)
      connection.subscribe(AdditionalLibraryRootsListener.TOPIC, rootListener)
    }

    // updates tabs names
    connection.subscribe(VirtualFileManager.VFS_CHANGES, MyVirtualFileListener())

    // Extends/cuts number of opened tabs. Also updates the location of tabs.
    connection.subscribe(UISettingsListener.TOPIC, MyUISettingsListener())

    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (this@FileEditorManagerImpl.project === project) {
          // Dispose created editors. We do not use closeEditor method because it fires events and changes history.
          closeAllFiles(repaint = false)
        }
      }
    })

    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        dumbModeFinished.tryEmit(Unit)
      }
    })

    processFileUpdateRequests()
  }

  @RequiresEdt
  internal suspend fun init(): kotlin.Pair<EditorsSplitters, EditorSplitterState?> {
    initJob.join()
    return mainSplitters to state.getAndSet(null)
  }

  companion object {
    @JvmField
    @Internal
    val DUMB_AWARE: Key<Boolean> = Key.create("DUMB_AWARE")

    @JvmField
    @TestOnly
    val ALLOW_IN_LIGHT_PROJECT: Key<Boolean> = Key.create("ALLOW_IN_LIGHT_PROJECT")

    @JvmField
    val CLOSING_TO_REOPEN: Key<Boolean> = Key.create("CLOSING_TO_REOPEN")

    /**
     * Works on VirtualFile objects and allows disabling the Preview Tab functionality for certain files.
     * If a virtual file has, this key is set to TRUE, the corresponding editor will always be opened in a regular tab.
     */
    @JvmField
    val FORBID_PREVIEW_TAB: Key<Boolean> = Key.create("FORBID_PREVIEW_TAB")

    @JvmField
    val OPEN_IN_PREVIEW_TAB: Key<Boolean> = Key.create("OPEN_IN_PREVIEW_TAB")

    /**
     * Works on [FileEditor] objects, allows forcing opening other editor tabs in the main window.
     * When determining a proper place to open a new editor tab, the currently selected file editor is checked
     * whether it has this key set to TRUE.
     * If that's the case, and the selected editor is a singleton in a split view,
     * the new editor tab is opened in the sibling of that split window.
     * If the singleton editor is not in a split view,
     * but in a separate detached window, then the new editors will be opened in the main window splitters.
     */
    @JvmField
    val SINGLETON_EDITOR_IN_WINDOW: Key<Boolean> = Key.create("OPEN_OTHER_TABS_IN_MAIN_WINDOW")

    const val FILE_EDITOR_MANAGER: String = "FileEditorManager"
    const val EDITOR_OPEN_INACTIVE_SPLITTER: String = "editor.open.inactive.splitter"
    private val openFileSetModificationCount = LongAdder()

    @JvmField
    val OPEN_FILE_SET_MODIFICATION_COUNT: ModificationTracker = ModificationTracker { openFileSetModificationCount.sum() }

    private fun isFileOpenInWindow(file: VirtualFile, window: EditorWindow): Boolean {
      val shouldFileBeSelected = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE
      return if (shouldFileBeSelected) file == window.selectedFile else window.isFileOpen(file)
    }

    @JvmStatic
    fun forbidSplitFor(file: VirtualFile): Boolean = file.getUserData(SplitAction.FORBID_TAB_SPLIT) == true

    internal fun getOriginalFile(file: VirtualFile): VirtualFile {
      return BackedVirtualFile.getOriginFileIfBacked(if (file is VirtualFileWindow) file.delegate else file)
    }
  }

  private fun processFileUpdateRequests() {
    coroutineScope.launch(CoroutineName("FileEditorManagerImpl file update")) {
      fileUpdateChannel.start(receiveFilter = ::isFileOpen)
    }
    coroutineScope.launch(CoroutineName("FileEditorManagerImpl file title update")) {
      fileTitleUpdateChannel.start(receiveFilter = { file -> file == null || isFileOpen(file) })
    }
  }

  private fun closeFilesOnFileEditorRemoval() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(object : ExtensionPointListener<FileEditorProvider> {
      override fun extensionRemoved(extension: FileEditorProvider, pluginDescriptor: PluginDescriptor) {
        for (editor in openedComposites) {
          for ((_, provider) in editor.allEditorsWithProviders) {
            if (provider == extension) {
              closeFile(editor.file)
              break
            }
          }
        }
      }
    }, this)
  }

  override fun dispose() {
    try {
      coroutineScope.cancel()
    }
    finally {
      for (composite in openedComposites) {
        Disposer.dispose(composite)
      }
    }
  }

  // need to open additional non-dumb-aware editors
  private suspend fun dumbModeFinished(project: Project, fileEditorProviderManager: FileEditorProviderManager) {
    refreshIcons()

    val fileToNewProviders = openedComposites.toList().mapNotNull { composite ->
      if (composite.providerSequence.none()) {
        // not initialized or invalid
        return@mapNotNull null
      }

      val newProviders = fileEditorProviderManager.getDumbUnawareProviders(project, composite.file, excludeIds = getEditorTypeIds(composite))
      if (newProviders.isEmpty()) {
        null
      }
      else {
        ProviderChange(composite = composite, newProviders = newProviders, editorTypeIdsToRemove = emptyList())
      }
    }

    for (item in fileToNewProviders) {
      updateFileEditorProviders(item)
    }

    // update for non-dumb-aware EditorTabTitleProviders
    scheduleUpdateFileName(file = null)
  }

  private suspend fun rootsChanged(fileEditorProviderManager: FileEditorProviderManager) {
    val fileToProviders = IdentityHashMap<VirtualFile, List<FileEditorProvider>>()
    val providerChanges = openedComposites.toList().mapNotNull { composite ->
      val file = composite.file
      val providers = fileToProviders.getOrPut(file) { fileEditorProviderManager.getProvidersAsync(project, file) }
      val editorTypeIds = providers.mapTo(LinkedHashSet()) { it.editorTypeId }
      val oldEditorTypeIds = getEditorTypeIds(composite)
      if (oldEditorTypeIds.isEmpty()) {
        // not initialized or invalid
        return@mapNotNull null
      }

      val newProviders = providers.filter { !oldEditorTypeIds.contains(it.editorTypeId) }
      val editorTypeIdsToRemove = oldEditorTypeIds.filter { !editorTypeIds.contains(it) }
      if (newProviders.isEmpty() && editorTypeIdsToRemove.isEmpty()) {
        null
      }
      else {
        ProviderChange(composite = composite, newProviders = newProviders, editorTypeIdsToRemove = editorTypeIdsToRemove)
      }
    }
    if (providerChanges.isEmpty()) {
      return
    }

    for (item in providerChanges) {
      updateFileEditorProviders(item)
    }

    scheduleUpdateFileName(file = null)
  }

  private suspend fun updateFileEditorProviders(item: ProviderChange) {
    withContext(Dispatchers.EDT) {
      val composite = item.composite
      for (editorTypeId in item.editorTypeIdsToRemove) {
        composite.removeEditor(editorTypeId)
      }

      for (provider in item.newProviders) {
        composite.addEditor(editor = provider.createEditor(project, item.composite.file), provider = provider)
      }

      for (splitters in getAllSplitters()) {
        splitters.updateFileBackgroundColorAsync(item.composite.file)
      }
    }
  }

  @RequiresEdt
  fun initDockableContentFactory() {
    if (contentFactory != null) {
      return
    }
    contentFactory = DockableEditorContainerFactory(fileEditorManager = this, coroutineScope = coroutineScope.childScope("DockableEditorContainerFactory"))
    DockManager.getInstance(project).register(DockableEditorContainerFactory.TYPE, contentFactory!!, this)
  }

  override val component: JComponent?
    get() = mainSplitters

  fun getAllSplitters(): Set<EditorsSplitters> {
    if (!initJob.isCompleted) {
      return emptySet()
    }

    val dockContainers = project.serviceIfCreated<DockManager>()?.containers ?: emptyList()
    if (dockContainers.isEmpty() || (dockContainers.singleOrNull() as? DockableEditorTabbedContainer)?.splitters === mainSplitters) {
      return setOf(mainSplitters)
    }

    // ordered
    val result = LinkedHashSet<EditorsSplitters>(dockContainers.size + 1)
    result.add(mainSplitters)
    for (container in dockContainers) {
      if (container is DockableEditorTabbedContainer) {
        result.add(container.splitters)
      }
    }
    return result
  }

  private fun getActiveSplittersAsync(): Deferred<EditorsSplitters?> {
    val result = CompletableDeferred<EditorsSplitters?>()
    val focusManager = IdeFocusManager.getGlobalInstance()
    focusManager.doWhenFocusSettlesDown {
      result.complete(if (project.isDisposed) null else getDockContainer(focusManager.focusOwner)?.splitters ?: mainSplitters)
    }
    return result
  }

  @RequiresEdt
  private fun getActiveSplitterSync(): EditorsSplitters {
    if (Registry.`is`("ide.navigate.to.recently.focused.editor", false)) {
      getLastFocusedSplitters()?.let { return it }
    }

    val focusManager = IdeFocusManager.getGlobalInstance()
    val focusOwner = focusManager.focusOwner
                     ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                     ?: focusManager.getLastFocusedFor(focusManager.lastFocusedIdeWindow)
    val container = getDockContainer(focusOwner)
                    ?: getDockContainer(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow)
    return container?.splitters ?: mainSplitters
  }

  private fun getDockContainer(focusOwner: Component?): DockableEditorTabbedContainer? {
    return DockManager.getInstance(project)
      .getContainerFor(focusOwner) { it is DockableEditorTabbedContainer } as DockableEditorTabbedContainer?
  }

  override val preferredFocusedComponent: JComponent?
    get() = currentFileEditorFlow.value?.preferredFocusedComponent

  /**
   * @return color of the `file` which corresponds to the file's status
   */
  fun getFileColor(file: VirtualFile): Color {
    return FileStatusManager.getInstance(project)?.getStatus(file)?.color ?: UIUtil.getLabelForeground()
  }

  open fun isProblem(file: VirtualFile): Boolean = false

  open fun getFileTooltipText(file: VirtualFile, composite: EditorComposite?): @NlsContexts.Tooltip String {
    val prefix = if (composite != null && composite.isPreview) "${LangBundle.message("preview.editor.tab.tooltip.text")} " else ""
    for (provider in EditorTabTitleProvider.EP_NAME.lazySequence()) {
      val text = try {
        provider.getEditorTabTooltipText(project, file) ?: continue
      }
      catch (ignore: IndexNotReadyException) {
      }
      return prefix + text
    }
    return prefix + FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
  }

  override fun updateFilePresentation(file: VirtualFile) {
    if (!isFileOpen(file)) {
      return
    }
    scheduleUpdateFileName(file)
    queueUpdateFile(file)
  }

  /**
   * Updates tab color for the specified `file`. The `file`
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  override fun updateFileColor(file: VirtualFile) {
    if (!initJob.isCompleted) {
      return
    }

    for (splitters in getAllSplitters()) {
      splitters.scheduleUpdateFileColor(file)
    }
  }

  private fun updateFileBackgroundColor(file: VirtualFile) {
    for (each in getAllSplitters()) {
      each.updateFileBackgroundColorAsync(file)
    }
  }

  /**
   * Updates tab icon for the specified `file`. The `file`
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  /**
   * Updates tab icon for the specified `file`. The `file`
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  protected fun updateFileIcon(file: VirtualFile, immediately: Boolean = false) {
    for (splitters in getAllSplitters()) {
      if (immediately) {
        splitters.updateFileIconImmediately(file = file, icon = IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, project))
      }
      else {
        splitters.scheduleUpdateFileIcon(file)
      }
    }
  }

  /**
   * Updates tab title and tab tool tip for the specified `file`.
   */
  private fun scheduleUpdateFileName(file: VirtualFile?) {
    // Queue here is to prevent title flickering when the tab is being closed and two events are arriving:
    // with component==null and component==next focused tab only the last event makes sense to handle
    fileTitleUpdateChannel.queue(file)
  }

  override fun unsplitAllWindow() {
    getActiveSplitterSync().currentWindow?.unsplitAll()
  }

  override val windowSplitCount: Int
    get() = getActiveSplitterSync().splitCount

  override fun hasSplitOrUndockedWindows(): Boolean = getAllSplitters().size > 1 || windowSplitCount > 1

  override val windows: Array<EditorWindow>
    get() = getAllSplitters().flatMap(EditorsSplitters::windows).toTypedArray()

  override fun getNextWindow(window: EditorWindow) = getNextWindowImpl(currentWindow = window, ascending = true)

  override fun getPrevWindow(window: EditorWindow) = getNextWindowImpl(currentWindow = window, ascending = false)

  private fun getNextWindowImpl(currentWindow: EditorWindow, ascending: Boolean): EditorWindow? {
    val windows = splitters.getOrderedWindows()
    val currentWindowIndex = windows.indexOf(currentWindow)
    if (currentWindowIndex != -1) {
      val nextWindowIndex = currentWindowIndex + (if (ascending) 1 else -1)
      return windows.get((nextWindowIndex + windows.size) % windows.size)
    }
    else {
      LOG.error("No window found")
      return null
    }
  }

  override fun createSplitter(orientation: Int, window: EditorWindow?) {
    // the window was available from action event, for example, when invoked from the tab menu of an editor that is not the 'current'
    (window ?: splitters.currentWindow)?.split(orientation = orientation, forceSplit = true, virtualFile = null, focusNew = false)
  }

  override fun changeSplitterOrientation() {
    splitters.currentWindow?.changeOrientation()
  }

  override val isInSplitter: Boolean
    get() {
      val currentWindow = splitters.currentWindow
      return currentWindow != null && currentWindow.inSplitter()
    }

  override fun hasOpenedFile(): Boolean = splitters.currentWindow?.selectedComposite != null

  override val currentFile: VirtualFile?
    get() {
      if (!ClientId.isCurrentlyUnderLocalId) {
        return clientFileEditorManager?.getSelectedFile()
      }
      if (!initJob.isCompleted) {
        return null
      }
      return getActiveSplitterSync().currentFile
    }

  override val activeWindow: CompletableFuture<EditorWindow?>
    get() = getActiveSplittersAsync().asCompletableFuture().thenApply { it?.currentWindow }

  override var currentWindow: EditorWindow?
    get() {
      if (!ClientId.isCurrentlyUnderLocalId || !initJob.isCompleted) {
        return null
      }
      if (!ApplicationManager.getApplication().isDispatchThread) {
        LOG.warn("Requesting getCurrentWindow() on BGT, returning null", Throwable())
        return null
      }
      return getActiveSplitterSync().currentWindow
    }
    set(window) {
      if (ClientId.isCurrentlyUnderLocalId) {
        getActiveSplitterSync().setCurrentWindow(window = window, requestFocus = true)
      }
    }

  /**
   * This method runs pre-close checks (e.g., confirmation dialogs) before closing the window
   * @return true if the window was closed; false otherwise
   */
  @RequiresEdt
  internal fun closeFile(window: EditorWindow, composite: EditorComposite, runChecks: Boolean): Boolean {
    val file = composite.file
    if (runChecks && !canCloseFile(file)) {
      return false
    }

    openFileSetModificationCount.increment()
    WriteIntentReadAction.run {
      window.closeFile(file = file, composite = composite)
    }
    return true
  }

  /**
   * Runs pre-close checks on virtual file
   * @return true if all the checks were successfully passed and the file can be closed
   */
  private fun canCloseFile(file: VirtualFile): Boolean {
    val checks = VirtualFilePreCloseCheck.EP_NAME.extensionsIfPointIsRegistered
    return checks.all { it.canCloseFile(file) }
  }

  @RequiresEdt
  override fun closeFileWithChecks(file: VirtualFile, window: EditorWindow): Boolean {
    return closeFile(window = window, composite = window.getComposite(file) ?: return false, runChecks = true)
  }

  @RequiresEdt
  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    closeFile(window = window, composite = window.getComposite(file) ?: return, runChecks = false)
  }

  override fun closeFile(file: VirtualFile) {
    closeFile(file = file, moveFocus = true, closeAllCopies = false)
  }

  @RequiresEdt
  fun closeFile(file: VirtualFile, moveFocus: Boolean, closeAllCopies: Boolean) {
    if (closeAllCopies) {
      ClientId.withClientId(ClientId.localId).use {
        openFileSetModificationCount.increment()
        for (each in getAllSplitters()) {
          runBulkTabChangeInEdt(each) { each.closeFile(file = file, moveFocus = moveFocus) }
        }
      }
      for (manager in allClientFileEditorManagers) {
        manager.closeFile(file = file, closeAllCopies = true)
      }
    }
    else {
      if (ClientId.isCurrentlyUnderLocalId) {
        openFileSetModificationCount.increment()
        val activeSplitters = getActiveSplitterSync()
        runBulkTabChangeInEdt(activeSplitters) {
          activeSplitters.closeFile(file = file, moveFocus = moveFocus)
        }
      }
      else {
        clientFileEditorManager?.closeFile(file = file, closeAllCopies = false)
      }
    }
  }

  private fun closeFileEditor(editor: FileEditor, moveFocus: Boolean = true) {
    val file = editor.file ?: return
    if (ClientId.isCurrentlyUnderLocalId) {
      openFileSetModificationCount.increment()
      for (each in getAllSplitters()) {
        runBulkTabChange(each) { each.closeFileEditor(file = file, editor = editor, moveFocus = moveFocus) }
      }
    }
    else {
      clientFileEditorManager?.closeFile(file, false) // todo pass editor inside ?
    }
  }

  private val allClientFileEditorManagers: List<ClientFileEditorManager>
    get() = project.getServices(ClientFileEditorManager::class.java, ClientKind.REMOTE)

  final override fun isFileOpenWithRemotes(file: VirtualFile): Boolean {
    return isFileOpen(file) || allClientFileEditorManagers.any { it.isFileOpen(file) }
  }

  final override fun openFile(file: VirtualFile, window: EditorWindow?, options: FileEditorOpenOptions): FileEditorComposite {
    require(file.isValid) { "file is not valid: $file" }

    var windowToOpenIn = window
    if (windowToOpenIn != null && windowToOpenIn.isDisposed) {
      windowToOpenIn = null
    }

    if (windowToOpenIn == null) {
      val mode = options.openMode ?: getOpenMode(IdeEventQueue.getInstance().trueCurrentEvent)
      if (mode == OpenMode.NEW_WINDOW) {
        if (options.reuseOpen) {
          val existingWindowAndComposite = (project.serviceIfCreated<DockManager>()?.containers?.asSequence() ?: emptySequence())
            .filterIsInstance<DockableEditorTabbedContainer>()
            .map { it.splitters }
            .filter { it != mainSplitters }
            .flatMap { sequenceOfNotNull(it.currentWindow) /* check current first */ + it.windows() }
            .mapNotNull {
              val composite = it.getComposite(file) ?: return@mapNotNull  null
              it to composite
            }
            .firstOrNull()
          if (existingWindowAndComposite != null) {
            val existingWindow = existingWindowAndComposite.first
            existingWindow.setSelectedComposite(file = file, focusEditor = options.requestFocus)
            if (options.requestFocus) {
              existingWindow.requestFocus(true)
              existingWindow.toFront()
            }
            return existingWindowAndComposite.second
          }
        }

        if (forbidSplitFor(file)) {
          closeFile(file)
        }
        return (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(
          file = file,
          fileEditorManager = this,
          isSingletonEditorInWindow = options.isSingletonEditorInWindow,
        ) { editorWindow ->
          if (forbidSplitFor(file = file) && !editorWindow.isFileOpen(file = file)) {
            closeFile(file = file)
          }
          doOpenFile(file = file, windowToOpenIn = editorWindow, options = options)
        }
      }
      else if (mode == OpenMode.RIGHT_SPLIT) {
        openInRightSplit(file, options.requestFocus)?.let {
          return it
        }
      }
    }

    if (windowToOpenIn == null) {
      if (options.reuseOpen || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)) {
        windowToOpenIn = findWindowInAllSplitters(file)
        if (windowToOpenIn != null && forbidSplitFor(file = file) && !windowToOpenIn.isFileOpen(file)) {
          closeFile(file)
        }
      }
      if (windowToOpenIn == null) {
        windowToOpenIn = getOrCreateCurrentWindow(file)
      }
    }

    return openFileImpl(window = windowToOpenIn, _file = file, entry = null, options = options)
  }

  private fun doOpenFile(file: VirtualFile, windowToOpenIn: EditorWindow, options: FileEditorOpenOptions): FileEditorComposite {
    if (forbidSplitFor(file = file) && !windowToOpenIn.isFileOpen(file)) {
      closeFile(file)
    }
    return openFileImpl(window = windowToOpenIn, _file = file, entry = null, options = options)
  }

  override suspend fun openFile(file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    val mode = options.openMode
    if (mode == OpenMode.NEW_WINDOW) {
      return withContext(Dispatchers.EDT) {
        if (forbidSplitFor(file)) {
          closeFile(file)
        }
        (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(
          file = file,
          fileEditorManager = this@FileEditorManagerImpl,
          isSingletonEditorInWindow = false,
        ) { editorWindow ->
          if (forbidSplitFor(file = file) && !editorWindow.isFileOpen(file = file)) {
            closeFile(file = file)
          }

          doOpenFile(file = file, windowToOpenIn = editorWindow, options = options)
        }
      }
    }
    else if (mode == OpenMode.RIGHT_SPLIT) {
      withContext(Dispatchers.EDT) {
        openInRightSplit(file, options.requestFocus)
      }?.let { composite ->
        if (composite is EditorComposite) {
          composite.waitForAvailable()
        }
        return composite
      }
    }

    val isCurrentlyUnderLocalId = ClientId.isCurrentlyUnderLocalId

    val reuseOpen = options.reuseOpen || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)
    var composite: FileEditorComposite? = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val existingWindow = if (reuseOpen) findWindowInAllSplitters(file) else null
        val window = existingWindow ?: getOrCreateCurrentWindow(file)
        if (forbidSplitFor(file) && !window.isFileOpen(file)) {
          closeFile(file)
        }

        if (!isCurrentlyUnderLocalId) {
          return@writeIntentReadAction null
        }

        @Suppress("DuplicatedCode")
        runBulkTabChangeInEdt(window.owner) {
          doOpenInEdt(window = window, file = file, options = options, fileEntry = null)
        }
      }
    }

    if (composite == null) {
      assert(!isCurrentlyUnderLocalId)
      composite = clientFileEditorManager?.openFileAsync(
        file = file,
        // it used to be passed as forceCreate=false there, so we need to pass it as reuseOpen=true
        // otherwise, any navigation will open a new editor composite which is invisible in RD mode
        options = options.copy(reuseOpen = true),
      ) ?: FileEditorComposite.EMPTY
    }

    // The client of the `openFile` API expects an editor to be available after invocation, so we wait until the file is opened
    if (composite is EditorComposite) {
      composite.waitForAvailable()
    }
    return composite
  }

  private fun findWindowInAllSplitters(file: VirtualFile): EditorWindow? {
    val activeCurrentWindow = getActiveSplitterSync().currentWindow
    if (activeCurrentWindow != null && isFileOpenInWindow(file, activeCurrentWindow)) {
      return activeCurrentWindow
    }

    for (splitters in getAllSplitters()) {
      for (window in splitters.windows()) {
        if (isFileOpenInWindow(file, window)) {
          if (AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)) {
            return window
          }
          // return a window from here so that we don't look for it again in getOrCreateCurrentWindow
          return activeCurrentWindow
        }
      }
    }
    return null
  }

  @RequiresEdt
  private fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val splitters = getActiveSplitterSync()
    val currentEditor = getSelectedEditor { splitters }
    val isSingletonEditor = isSingletonFileEditor(currentEditor)

    // If the selected editor is a singleton in a split window, prefer the sibling of that split window.
    // When navigating from a diff view, opened in a vertical split,
    // this makes a new tab open below/above the diff view, still keeping the diff in sight.
    if (isSingletonEditor) {
      val currentWindow = splitters.currentWindow
      if (currentWindow != null && currentWindow.inSplitter() &&
          currentWindow.tabCount == 1 &&
          currentWindow.selectedComposite?.selectedEditor === currentEditor) {
        currentWindow.siblings().firstOrNull()?.let {
          return it
        }
      }
    }

    val uiSettings = UISettings.getInstance()
    val useMainWindow = isSingletonEditor || uiSettings.openTabsInMainWindow
    val targetSplitters = if (useMainWindow) mainSplitters else splitters
    val targetWindow = targetSplitters.currentWindow
    if (targetWindow != null && uiSettings.editorTabPlacement == UISettings.TABS_NONE) {
      return targetWindow
    }
    return targetSplitters.getOrCreateCurrentWindow(file)
  }

  fun openFileInNewWindow(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFile(
      file = file,
      window = null,
      options = FileEditorOpenOptions(
        requestFocus = true,
        openMode = OpenMode.NEW_WINDOW,
      ),
    ).retrofit()
  }

  @RequiresEdt
  private fun openInRightSplit(file: VirtualFile, requestFocus: Boolean): FileEditorComposite? {
    val window = splitters.currentWindow ?: return null
    if (window.inSplitter()) {
      val composite = window.siblings().lastOrNull()?.composites()?.firstOrNull { it.file == file }
      if (composite != null) {
        // already in right splitter
        if (requestFocus) {
          window.setCurrentCompositeAndSelectTab(composite)
          focusEditorOnComposite(composite = composite, splitters = window.owner)
        }
        return composite
      }
    }
    return window.owner.openInRightSplit(file)?.composites()?.firstOrNull { it.file == file }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use public API.", level = DeprecationLevel.ERROR)
  fun openFileImpl2(window: EditorWindow, file: VirtualFile, focusEditor: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileImpl2(window = window, file = file, options = FileEditorOpenOptions(requestFocus = focusEditor)).retrofit()
  }

  open fun openFileImpl2(window: EditorWindow, file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    if (forbidSplitFor(file) && !window.isFileOpen(file)) {
      closeFile(file)
    }
    return openFileImpl(window = window, _file = file, entry = null, options = options)
  }

  internal suspend fun checkForbidSplitAndOpenFile(window: EditorWindow, file: VirtualFile, options: FileEditorOpenOptions) {
    if (forbidSplitFor(file) && !window.isFileOpen(file)) {
      closeFile(file)
    }

    if (!ClientId.isCurrentlyUnderLocalId) {
      clientFileEditorManager?.openFileAsync(
        file = file,
        // it used to be passed as forceCreate=false there, so we need to pass it as reuseOpen=true
        // otherwise, any navigation will open a new editor composite which is invisible in RD mode
        options = options.copy(reuseOpen = true),
      )
      return
    }

    withContext(Dispatchers.EDT) {
      runBulkTabChangeInEdt(window.owner) {
        doOpenInEdt(window = window, file = file, options = options, fileEntry = null)
      }
    }
  }

  private val clientFileEditorManager: ClientFileEditorManager?
    get() {
      // todo RDCT-78
      val session = ClientSessionsManager.getProjectSession(project) ?: return null
      LOG.assertTrue(!session.isLocal, "Trying to get ClientFileEditorManager for local ClientId")
      return session.serviceOrNull<ClientFileEditorManager>()
    }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  @Suppress("DuplicatedCode")
  internal fun openFileImpl(
    window: EditorWindow,
    @Suppress("LocalVariableName") _file: VirtualFile,
    entry: FileEntry?,
    options: FileEditorOpenOptions,
  ): FileEditorComposite {
    assert(EDT.isCurrentThreadEdt() || !ApplicationManager.getApplication().isReadAccessAllowed) {
      "must not attempt opening files under read action"
    }

    val file = getOriginalFile(_file)
    if (!ClientId.isCurrentlyUnderLocalId) {
      // it used to be passed as forceCreate=false there, so we need to pass it as reuseOpen=true
      // otherwise, any navigation will open a new editor composite which is invisible in RD mode
      return openFileUsingClient(file, options.copy(reuseOpen = true))
    }

    if (!file.isValid) {
      LOG.error(InvalidVirtualFileAccessException(file))
    }

    fun open(): FileEditorComposite {
      return runBulkTabChangeInEdt(window.owner) {
        doOpenInEdt(
          window = window,
          file = file,
          fileEntry = entry,
          options = options,
        ) ?: FileEditorComposite.EMPTY
      }
    }

    if (EDT.isCurrentThreadEdt()) {
      return WriteIntentReadAction.compute(Computable {
        val composite = open()
        if (composite is EditorComposite) {
          if (options.waitForCompositeOpen) {
            blockingWaitForCompositeFileOpen(composite)
            if (composite.providerSequence.none()) {
              closeFile(window = window, composite = composite, runChecks = false)
              return@Computable FileEditorComposite.EMPTY
            }
          }
          else {
            scheduleCloseIfEmpty(window, composite)
          }
        }
        return@Computable composite
      })
    }
    else {
      return runBlockingCancellable {
        val composite = withContext(Dispatchers.EDT) {
          open()
        }
        if ( composite is EditorComposite) {
          if (options.waitForCompositeOpen) {
            composite.waitForAvailable()
            if (composite.providerSequence.none()) {
              withContext(Dispatchers.EDT) {
                closeFile(window = window, composite = composite, runChecks = false)
              }
              return@runBlockingCancellable FileEditorComposite.EMPTY
            }
          }
          else {
            scheduleCloseIfEmpty(window, composite)
          }
        }
        composite
      }
    }
  }

  private fun scheduleCloseIfEmpty(
    window: EditorWindow,
    composite: EditorComposite,
  ) {
    window.coroutineScope.launch {
      composite.waitForAvailable()
      if (composite.providerSequence.none()) {
        attachAsChildTo(composite.coroutineScope)
        LOG.warn("Composite is closed because empty (composite=$composite)")
        withContext(Dispatchers.EDT) {
          if (composite.providerSequence.none()) {
            closeFile(window = window, composite = composite, runChecks = false)
          }
        }
      }
    }
  }

  @Suppress("DuplicatedCode")
  @RequiresEdt
  internal fun openFileInNewCompositeInEdt(
    window: EditorWindow,
    file: VirtualFile,
    fileEntry: FileEntry?,
    options: FileEditorOpenOptions,
  ): FileEditorComposite? {
    EDT.assertIsEdt()

    val effectiveFile = getOriginalFile(file)
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.openFile(file = file, options) ?: return FileEditorComposite.EMPTY
    }

    return runBulkTabChangeInEdt(window.owner) {
      doOpenInEdt(
        window = window,
        file = effectiveFile,
        options = options,
        fileEntry = fileEntry,
        forceCompositeCreation = true,
      )
    }
  }

  protected open suspend fun canOpenFileAsync(file: VirtualFile, providers: List<FileEditorProvider>): Boolean = !providers.isEmpty()

  private fun openFileUsingClient(file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    val clientManager = clientFileEditorManager ?: return FileEditorComposite.EMPTY
    return clientManager.openFile(file = file, options)
  }

  private fun doOpenInEdt(
    window: EditorWindow,
    file: VirtualFile,
    fileEntry: FileEntry?,
    options: FileEditorOpenOptions,
    forceCompositeCreation: Boolean = false,
  ): EditorComposite? {
    return WriteIntentReadAction.compute(Computable {
      var composite = if (forceCompositeCreation) null else window.getComposite(file)
      val isNewEditor = composite == null
      if (composite == null) {
        composite = createCompositeAndModel(file = file, window = window, fileEntry = fileEntry) ?: return@Computable null
        openedCompositeEntries.add(EditorCompositeEntry(composite = composite, delayedState = null))
      }

      window.addComposite(
        composite = composite,
        file = composite.file,
        options = options,
        isNewEditor = isNewEditor,
      )

      if (isNewEditor) {
        openFileSetModificationCount.increment()
      }
      else {
        for (editorWithProvider in composite.allEditorsWithProviders) {
          restoreEditorState(
            file = file,
            fileEditorWithProvider = editorWithProvider,
            isNewEditor = false,
            exactState = options.isExactState,
            project = project,
          )
        }

        // restore selected editor
        val provider = (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl)
          .getSelectedFileEditorProvider(composite = composite, project = project)
        if (provider != null) {
          composite.setSelectedEditor(provider)
        }

        window.owner.afterFileOpen(file)
      }

      selectionHistory.addRecord(file to window)

      // update frame and tab title
      scheduleUpdateFileName(file)
      return@Computable composite
    })
  }

  @RequiresEdt
  open fun createCompositeAndModel(
    file: VirtualFile,
    window: EditorWindow,
    fileEntry: FileEntry? = null,
  ): EditorComposite? {
    val compositeCoroutineScope = window.owner.coroutineScope.childScope("EditorComposite(file=${file.name})")
    val model = createEditorCompositeModel(
      coroutineScope = compositeCoroutineScope,
      editorPropertyChangeListener = editorPropertyChangeListener,
      fileProvider = { file },
      project = project,
      fileEntry = fileEntry,
    )
    val composite = createCompositeByEditorWithModel(
      file = file,
      model = model,
      coroutineScope = compositeCoroutineScope,
    ) ?: return null
    composite.initDeferred.complete(Unit)
    return composite
  }

  internal fun createEditorCompositeModelOnStartup(
    compositeCoroutineScope: CoroutineScope,
    fileProvider: suspend () -> VirtualFile,
    fileEntry: FileEntry?,
    isLazy: Boolean,
  ): Flow<EditorCompositeModel> {
    // we don't check canOpenFile (we don't have providers yet) -
    // empty windows will be removed later if needed, it should be quite a rare case
    val flow = createEditorCompositeModel(
      coroutineScope = compositeCoroutineScope,
      editorPropertyChangeListener = editorPropertyChangeListener,
      fileProvider = fileProvider,
      project = project,
      fileEntry = fileEntry,
    )
    return if (isLazy) flow else flow.shareIn(compositeCoroutineScope, started = SharingStarted.Eagerly, replay = 1)
  }

  // only for remote dev
  protected fun createCompositeModelByProvidedList(editorsWithProviders: List<FileEditorWithProvider>): Flow<EditorCompositeModel> {
    return EditorCompositeModelManager(editorPropertyChangeListener, coroutineScope).blockingFileEditorWithProviderFlow(
      editorsWithProviders = editorsWithProviders,
    )
  }

  @RequiresEdt
  fun createCompositeByEditorWithModel(
    file: VirtualFile,
    model: Flow<EditorCompositeModel>,
    coroutineScope: CoroutineScope,
  ): EditorComposite? {
    val composite = createCompositeInstance(
      file = file,
      model = model,
      coroutineScope = coroutineScope,
    ) ?: return null
    coroutineScope.launch {
      // listen for preview status change to update file tooltip, skip the first value as it is the initial value
      composite.isPreviewFlow.drop(1).collect {
        scheduleUpdateFileName(file)
      }
    }
    return composite
  }

  @RequiresEdt
  protected fun createCompositeInstance(
    file: VirtualFile,
    model: Flow<EditorCompositeModel>,
    coroutineScope: CoroutineScope,
  ): EditorComposite? {
    if (ClientId.isCurrentlyUnderLocalId) {
      // the only place this class is created, won't be needed when we get rid of EditorWithProviderComposite usages
      @Suppress("DEPRECATION")
      return EditorWithProviderComposite(
        file = file,
        model = model,
        project = project,
        coroutineScope = coroutineScope,
      )
    }
    else {
      return clientFileEditorManager?.createComposite(
        file = file,
        coroutineScope = coroutineScope,
        model = model,
      )
    }
  }

  override fun notifyPublisher(runnable: Runnable) {
    runnable.run()
  }

  override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) {
    if (!ClientId.isCurrentlyUnderLocalId) {
      clientFileEditorManager?.setSelectedEditor(file, fileEditorProviderId)
      return
    }

    val composite = getComposite(file) ?: return
    composite.setSelectedEditor(fileEditorProviderId)
    // todo move to setSelectedEditor()?
    composite.selectedWithProvider?.fileEditor?.selectNotify()
  }

  override fun openFileEditor(descriptor: FileEditorNavigatable, focusEditor: Boolean): List<FileEditor> {
    return openEditorImpl(descriptor = descriptor, focusEditor = focusEditor).first
  }

  /**
   * @return the list of opened editors, and the one of them that was selected (if any)
   */
  @RequiresEdt
  private fun openEditorImpl(descriptor: FileEditorNavigatable, focusEditor: Boolean): kotlin.Pair<List<FileEditor>, FileEditor?> {
    val effectiveDescriptor: FileEditorNavigatable
    if (descriptor is OpenFileDescriptor && descriptor.getFile() is VirtualFileWindow) {
      val delegate = descriptor.getFile() as VirtualFileWindow
      val hostOffset = delegate.documentWindow.injectedToHost(descriptor.offset)
      val fixedDescriptor = OpenFileDescriptor(descriptor.project, delegate.delegate, hostOffset)
      fixedDescriptor.isUseCurrentWindow = descriptor.isUseCurrentWindow()
      fixedDescriptor.isUsePreviewTab = descriptor.isUsePreviewTab()
      effectiveDescriptor = fixedDescriptor
    }
    else {
      effectiveDescriptor = descriptor
    }

    val file = effectiveDescriptor.file
    val openOptions = FileEditorOpenOptions(
      reuseOpen = !effectiveDescriptor.isUseCurrentWindow,
      usePreviewTab = effectiveDescriptor.isUsePreviewTab,
      requestFocus = focusEditor,
      openMode = getOpenMode(IdeEventQueue.getInstance().trueCurrentEvent),
    )

    val fileEditors = openFile(file = file, window = null, options = openOptions).allEditors

    val currentCompositeForFile = getComposite(file)
    for (editor in fileEditors) {
      // try to navigate opened editor
      if (editor is NavigatableFileEditor && currentCompositeForFile?.selectedWithProvider?.fileEditor === editor &&
          navigateAndSelectEditor(editor, effectiveDescriptor, currentCompositeForFile)) {
        return fileEditors to editor
      }
    }

    for (editor in fileEditors) {
      // try other editors
      if (editor is NavigatableFileEditor && currentCompositeForFile?.selectedWithProvider?.fileEditor !== editor &&
          navigateAndSelectEditor(editor, effectiveDescriptor, currentCompositeForFile)) {
        return fileEditors to editor
      }
    }
    return fileEditors to null
  }

  override fun getProject(): Project = project

  override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? {
    val (fileEditors, selectedEditor) = openEditorImpl(descriptor = descriptor, focusEditor = focusEditor)
    if (fileEditors.isEmpty()) {
      return null
    }
    else if (fileEditors.size == 1) {
      return (fileEditors.first() as? TextEditor)?.editor
    }

    val textEditors = fileEditors.mapNotNull { it as? TextEditor }
    if (textEditors.isEmpty()) {
      return null
    }

    var target = if (selectedEditor is TextEditor) selectedEditor else textEditors.first()
    if (textEditors.size > 1) {
      val editorsWithProviders = getComposite(target)!!.allEditorsWithProviders
      val textProviderId = TextEditorProvider.getInstance().editorTypeId
      for (editorWithProvider in editorsWithProviders) {
        val editor = editorWithProvider.fileEditor
        if (editor is TextEditor && editorWithProvider.provider.editorTypeId == textProviderId) {
          target = editor
          break
        }
      }
    }

    getComposite(target)?.setSelectedEditor(target)
    return target.editor
  }

  override fun getSelectedEditorWithRemotes(): Collection<FileEditor> {
    val editorList = getSelectedEditorList()
    val editorManagerList = allClientFileEditorManagers
    if (editorManagerList.isEmpty()) {
      return editorList
    }

    val result = ArrayList<FileEditor>()
    result.addAll(editorList)
    for (m in editorManagerList) {
      result.addAll(m.getSelectedEditors())
    }
    return result
  }

  override fun getSelectedTextEditorWithRemotes(): Array<Editor> {
    val result = ArrayList<Editor>()
    for (e in selectedEditorWithRemotes) {
      if (e is TextEditor) {
        result.add(e.editor)
      }
    }
    return result.toTypedArray()
  }

  override fun getSelectedTextEditor(): Editor? = getSelectedTextEditor(isLockFree = false)

  final override fun getSelectedTextEditor(isLockFree: Boolean): Editor? {
    if (!initJob.isCompleted) {
      return null
    }

    if (!ClientId.isCurrentlyUnderLocalId) {
      val selectedEditor = (clientFileEditorManager ?: return null).getSelectedEditor()
      return if (selectedEditor is TextEditor) selectedEditor.editor else null
    }

    IntentionPreviewUtils.getPreviewEditor()?.let { return it }

    if (!isLockFree) {
      EDT.isCurrentThreadEdt()
    }

    val selectedEditor = ((if (isLockFree || !EDT.isCurrentThreadEdt()) mainSplitters else getActiveSplitterSync())
      .currentCompositeFlow.value)?.selectedEditor
    return (selectedEditor as? TextEditor)?.editor
  }

  override fun isFileOpen(file: VirtualFile): Boolean {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return (clientFileEditorManager ?: return false).isFileOpen(file)
    }
    return openedComposites.any { it.file == file }
  }

  override fun getOpenFiles(): Array<VirtualFile> = VfsUtilCore.toVirtualFileArray(openedFiles)

  val openedFiles: List<VirtualFile>
    get() {
      if (!ClientId.isCurrentlyUnderLocalId) {
        return clientFileEditorManager?.getAllFiles() ?: emptyList()
      }

      return locallyOpenedFiles
    }

  private val locallyOpenedFiles: List<VirtualFile>
    get() {
      val files = ArrayList<VirtualFile>()
      for (composite in openedComposites) {
        val file = composite.file
        if (!files.contains(file)) {
          files.add(file)
        }
      }
      return files
    }

  override fun getOpenFilesWithRemotes(): List<VirtualFile> {
    val result = locallyOpenedFiles.toMutableList()
    for (m in allClientFileEditorManagers) {
      result.addAll(m.getAllFiles())
    }
    return result
  }

  override fun hasOpenFiles(): Boolean {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getAllComposites()?.isNotEmpty() ?: false
    }

    return !openedCompositeEntries.isEmpty()
  }

  override fun getSelectedFiles(): Array<VirtualFile> {
    if (!initJob.isCompleted) {
      return VirtualFile.EMPTY_ARRAY
    }
    if (!ClientId.isCurrentlyUnderLocalId) {
      return (clientFileEditorManager ?: return VirtualFile.EMPTY_ARRAY).getSelectedFiles().toTypedArray()
    }

    val selectedFiles = LinkedHashSet<VirtualFile>()
    val activeSplitters = splitters
    selectedFiles.addAll(activeSplitters.selectedFiles)
    for (each in getAllSplitters()) {
      if (each !== activeSplitters) {
        selectedFiles.addAll(each.selectedFiles)
      }
    }
    return VfsUtilCore.toVirtualFileArray(selectedFiles)
  }

  override fun getSelectedEditors(): Array<FileEditor> {
    val result = getSelectedEditorList()
    return if (result.isEmpty()) FileEditor.EMPTY_ARRAY else result.toArray(FileEditor.EMPTY_ARRAY)
  }

  private fun getSelectedEditorList(): Collection<FileEditor> {
    if (!initJob.isCompleted) {
      return emptyList()
    }

    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditors() ?: emptyList()
    }
    val selectedEditors = SmartHashSet<FileEditor>()
    for (splitters in getAllSplitters()) {
      splitters.addSelectedEditorsTo(selectedEditors)
    }
    return selectedEditors
  }

  override val splitters: EditorsSplitters
    get() = if (ApplicationManager.getApplication().isDispatchThread) getActiveSplitterSync() else mainSplitters

  @get:RequiresEdt
  override val activeSplittersComposites: List<EditorComposite>
    get() = if (initJob.isCompleted) getActiveSplitterSync().getAllComposites() else emptyList()

  fun getLastFocusedSplitters(): EditorsSplitters? {
    if (ApplicationManager.getApplication().isDispatchThread) {
      val splitters = getAllSplitters().toMutableList()
      if (!splitters.isEmpty()) {
        splitters.sortWith { o1, o2 ->
          o2.lastFocusGainedTime.compareTo(o1.lastFocusGainedTime)
        }
        return splitters[0]
      }
    }
    return null
  }

  override fun getSelectedEditor(): FileEditor? = getSelectedEditor { splitters }

  @Internal
  fun getLastFocusedEditor(): FileEditor? = getSelectedEditor { getLastFocusedSplitters() ?: splitters }

  private inline fun getSelectedEditor(splitters: () -> EditorsSplitters): FileEditor? {
    return when {
      !ClientId.isCurrentlyUnderLocalId -> clientFileEditorManager?.getSelectedEditor()
      !initJob.isCompleted -> null
      else -> splitters().currentWindow?.selectedComposite?.selectedEditor ?: super.getSelectedEditor()
    }
  }

  @RequiresEdt
  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? = getComposite(file)?.selectedWithProvider

  @RequiresEdt
  override fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return retrofitEditorComposite(getComposite(file))
  }

  @RequiresEdt
  final override fun getEditors(file: VirtualFile): Array<FileEditor> {
    return getComposite(file)?.allEditors?.toTypedArray() ?: FileEditor.EMPTY_ARRAY
  }

  final override fun getEditorList(file: VirtualFile): List<FileEditor> = getComposite(file)?.allEditors ?: emptyList()

  override fun getAllEditorList(file: VirtualFile): MutableList<FileEditor> {
    val result = ArrayList<FileEditor>()
    // reuse getAllComposites(file)? Are there cases some composites are not accessible via splitters?
    for (composite in openedComposites) {
      if (composite.file == file) {
        result.addAll(composite.allEditors)
      }
    }
    for (clientManager in allClientFileEditorManagers) {
      result.addAll(clientManager.getEditors(file))
    }
    return if (result.isEmpty()) java.util.List.of() else result
  }

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> {
    val list = getAllEditorList(file)
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    return if (list.isEmpty()) FileEditor.EMPTY_ARRAY else (list as java.util.Collection<FileEditor>).toArray(FileEditor.EMPTY_ARRAY)
  }

  @RequiresEdt
  override fun getComposite(file: VirtualFile): EditorComposite? {
    val originalFile = getOriginalFile(file)
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getComposite(originalFile)
    }

    if (openedCompositeEntries.isEmpty()) {
      return null
    }

    return splitters.currentWindow?.getComposite(originalFile) ?: openedComposites.firstOrNull { it.file == originalFile }
  }

  fun getAllComposites(file: VirtualFile): List<EditorComposite> {
    if (ClientId.isCurrentlyUnderLocalId) {
      return getAllSplitters().flatMap { it.getAllComposites(file) }
    }
    else {
      return clientFileEditorManager?.getAllComposites(file) ?: emptyList()
    }
  }

  override fun getAllEditors(): Array<FileEditor> {
    if (!initJob.isCompleted) {
      return FileEditor.EMPTY_ARRAY
    }

    val result = ArrayList<FileEditor>()
    for (composite in openedComposites) {
      result.addAll(composite.allEditors)
    }
    for (clientManager in allClientFileEditorManagers) {
      result.addAll(clientManager.getAllEditors())
    }
    return result.toTypedArray()
  }

  final override suspend fun waitForTextEditors() {
    if (!initJob.isCompleted) {
      return
    }

    for (composite in openedComposites) {
      for ((editor, provider) in composite.allEditorsWithProviders) {
        // wait only for our platform regular text editors
        if (provider.editorTypeId == TEXT_EDITOR_PROVIDER_TYPE_ID && editor is TextEditor) {
          AsyncEditorLoader.waitForCompleted(editor.editor)
        }
      }
    }
  }

  @RequiresEdt
  fun getTopComponents(editor: FileEditor): List<JComponent> {
    return getComposite(editor)?.getTopComponents(editor) ?: emptyList()
  }

  @RequiresEdt
  override fun addTopComponent(editor: FileEditor, component: JComponent) {
    getComposite(editor)?.addTopComponent(editor, component)
  }

  @RequiresEdt
  override fun removeTopComponent(editor: FileEditor, component: JComponent) {
    getComposite(editor)?.removeTopComponent(editor, component)
  }

  @RequiresEdt
  override fun addBottomComponent(editor: FileEditor, component: JComponent) {
    getComposite(editor)?.addBottomComponent(editor, component)
  }

  @RequiresEdt
  override fun removeBottomComponent(editor: FileEditor, component: JComponent) {
    getComposite(editor)?.removeBottomComponent(editor, component)
  }

  override fun addFileEditorManagerListener(listener: FileEditorManagerListener) {
    listenerList.add(listener)
  }

  override fun removeFileEditorManagerListener(listener: FileEditorManagerListener) {
    listenerList.remove(listener)
  }

  override fun getState(): Element? {
    if (!initJob.isCompleted) {
      return null
    }

    val state = Element("state")
    mainSplitters.writeExternal(
      element = state,
      delayedStates = openedCompositeEntries.asSequence().filter { it.delayedState != null }.associateBy(keySelector = { it.composite }, valueTransform = { it.delayedState!! }),
    )
    return state
  }

  override fun loadState(state: Element) {
    this.state.set(EditorSplitterState(state).takeIf { it.leaf != null || it.splitters != null })
  }

  open fun getComposite(editor: FileEditor): EditorComposite? {
    return openedComposites.lastOrNull { it.containsFileEditor(editor) }
           ?: allClientFileEditorManagers.firstNotNullOfOrNull { it.getComposite(editor) }
  }

  private suspend fun fireSelectionChanged(
    newState: SelectionState?,
    oldEditorWithProvider: FileEditorWithProvider?,
    newEditorWithProvider: FileEditorWithProvider?,
    publisher: FileEditorManagerListener,
  ) {
    oldEditorWithProvider?.fileEditor?.deselectNotify()
    val newEditor = newEditorWithProvider?.fileEditor
    if (newEditor != null) {
      writeIntentReadAction {
        newEditor.selectNotify()
      }
      FileEditorCollector.logAlternativeFileEditorSelected(project = project, file = newState!!.composite.file, editor = newEditor)
      (serviceAsync<FileEditorProviderManager>() as FileEditorProviderManagerImpl).providerSelected(newState.composite)
    }

    writeIntentReadAction {
      publisher.selectionChanged(FileEditorManagerEvent(
        manager = this,
        oldEditorWithProvider = oldEditorWithProvider,
        newEditorWithProvider = newEditorWithProvider,
      ))
    }
  }

  override fun isChanged(editor: EditorComposite): Boolean {
    val status = (FileStatusManager.getInstance(project) ?: return false).getStatus(editor.file)
    return status !== FileStatus.UNKNOWN && status !== FileStatus.NOT_CHANGED
  }

  internal fun disposeComposite(composite: EditorComposite) {
    if (!ClientId.isCurrentlyUnderLocalId) {
      clientFileEditorManager?.removeComposite(composite)
      return
    }

    openedCompositeEntries.removeIf { it.composite == composite }
    composite.selectedEditorWithProvider.value?.fileEditor?.deselectNotify()
    splitters.onDisposeComposite(composite)

    for ((editor, provider) in composite.allEditorsWithProviders.asReversed()) {
      editor.removePropertyChangeListener(editorPropertyChangeListener)
      provider.disposeEditor(editor)
    }
    Disposer.dispose(composite)
  }

  /**
   * Closes deleted files. Closes file which is in the deleted directories.
   */
  private inner class MyVirtualFileListener : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
      for (event in events) {
        if (event is VFileDeleteEvent) {
          beforeFileDeletion(event)
        }
      }
    }

    override fun after(events: List<VFileEvent>) {
      for (event in events) {
        if (event is VFilePropertyChangeEvent) {
          propertyChanged(event)
        }
        else if (event is VFileMoveEvent) {
          fileMoved(event)
        }
      }
    }

    @RequiresEdt
    private fun beforeFileDeletion(event: VFileDeleteEvent) {
      val file = event.file
      for (openedFile in openFilesWithRemotes.asReversed()) {
        if (VfsUtilCore.isAncestor(file, openedFile, false)) {
          closeFile(file = openedFile, moveFocus = true, closeAllCopies = true)
        }
      }
    }

    private fun propertyChanged(event: VFilePropertyChangeEvent) {
      if (VirtualFile.PROP_NAME == event.propertyName) {
        EDT.isCurrentThreadEdt()
        val file = event.file
        if (isFileOpen(file)) {
          scheduleUpdateFileName(file)
          updateFileIcon(file) // file type can change after renaming
          updateFileBackgroundColor(file)
        }
      }
      else if (VirtualFile.PROP_WRITABLE == event.propertyName || VirtualFile.PROP_ENCODING == event.propertyName) {
        updateIcon(event)
      }
    }

    @RequiresEdt
    private fun updateIcon(event: VFilePropertyChangeEvent) {
      val file = event.file
      if (isFileOpen(file)) {
        updateFileIcon(file)
      }
    }

    private fun fileMoved(e: VFileMoveEvent) {
      val file = e.file
      for (openFile in openedFiles) {
        if (VfsUtilCore.isAncestor(file, openFile, false)) {
          scheduleUpdateFileName(openFile)
          updateFileBackgroundColor(openFile)
        }
      }
    }
  }

  private inner class MyEditorPropertyChangeListener : PropertyChangeListener {
    @RequiresEdt
    override fun propertyChange(e: PropertyChangeEvent) {
      if (project.isDisposed) {
        return
      }

      val propertyName = e.propertyName
      if (FileEditor.getPropModified() == propertyName) {
        getComposite(e.source as FileEditor)?.let {
          updateFileIcon(it.file)
        }
      }
      else if (FileEditor.getPropValid() == propertyName) {
        if (e.newValue == false) {
          closeFileEditor(e.source as FileEditor)
        }
      }
    }
  }

  /**
   * Gets events from VCS and updates color of myEditor tabs
   */
  private inner class MyFileStatusListener : FileStatusListener {
    // update color of all open files
    @RequiresEdt
    override fun fileStatusesChanged() {
      LOG.debug("FileEditorManagerImpl.MyFileStatusListener.fileStatusesChanged()")
      for (file in openedFiles.asReversed()) {
        coroutineScope.launch(Dispatchers.EDT) {
          LOG.debug { "updating file status in tab for ${file.path}" }
          updateFileStatus(file)
        }
      }
    }

    // update color of the file (if necessary)
    override fun fileStatusChanged(file: VirtualFile) {
      EDT.isCurrentThreadEdt()
      if (isFileOpen(file)) {
        updateFileStatus(file)
      }
    }

    private fun updateFileStatus(file: VirtualFile) {
      updateFileColor(file)
      updateFileIcon(file)
    }
  }

  /**
   * Gets events from FileTypeManager and updates icons on tabs
   */
  private inner class MyFileTypeListener : FileTypeListener {
    @RequiresEdt
    override fun fileTypesChanged(event: FileTypeEvent) {
      for (file in openedFiles.asReversed()) {
        updateFileIcon(file = file, immediately = true)
      }
    }
  }

  private class MyRootListener(private val fileEditorManager: FileEditorManagerImpl) : ModuleRootListener, AdditionalLibraryRootsListener {
    private val EDITOR_FILE_SWAPPER_EP_NAME = ExtensionPointName<EditorFileSwapper>("com.intellij.editorFileSwapper")

    @OptIn(FlowPreview::class)
    private val rootChangedRequests by lazy {
      val flow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      fileEditorManager.coroutineScope.launch {
        flow
          .debounce(100.milliseconds)
          .collectLatest {
            val replacements = smartReadAction(fileEditorManager.project) {
              computeEditorReplacements(fileEditorManager.openedComposites)
            }

            if (!replacements.isEmpty()) {
              withContext(Dispatchers.EDT) {
                replaceEditors(replacements)
              }
            }

            fileEditorManager.rootsChanged(serviceAsync<FileEditorProviderManager>())
          }
      }
      flow
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      check(rootChangedRequests.tryEmit(Unit))
    }

    private fun computeEditorReplacements(composites: Sequence<EditorComposite>): Map<EditorComposite, kotlin.Pair<VirtualFile, Int?>> {
      val swappers = EDITOR_FILE_SWAPPER_EP_NAME.extensionList
      return composites.mapNotNull { composite ->
        if (composite.file.isValid) {
          for (swapper in swappers) {
            val fileAndOffset = swapper.getFileToSwapTo(fileEditorManager.project, composite) ?: continue
            return@mapNotNull composite to fileAndOffset
          }
        }
        null
      }.associate { it }
    }

    private fun replaceEditors(replacements: Map<EditorComposite, kotlin.Pair<VirtualFile, Int?>>) {
      for (window in fileEditorManager.windows) {
        val selected = window.selectedComposite
        val composites = window.allComposites
        for ((index, composite) in composites.withIndex()) {
          if (!composite.file.isValid) {
            continue
          }

          val newFilePair = replacements.get(composite) ?: continue
          val newFile = newFilePair.first
          // already open
          if (window.isFileOpen(newFile)) {
            continue
          }

          val openResult = fileEditorManager.openFileImpl2(
            window = window,
            file = newFile,
            options = FileEditorOpenOptions(index = index, requestFocus = composite === selected),
          )
          val position = newFilePair.second
          if (position != null) {
            val openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(openResult.allEditors)?.editor
            if (openedEditor != null) {
              openedEditor.caretModel.moveToOffset(position)
              openedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
          }
          fileEditorManager.closeFile(window = window, composite = composite, runChecks = false)
        }
      }
    }

    override fun libraryRootsChanged(presentableLibraryName: @Nls String?,
                                     oldRoots: Collection<VirtualFile>,
                                     newRoots: Collection<VirtualFile>,
                                     libraryNameForDebug: String) {
      check(rootChangedRequests.tryEmit(Unit))
    }
  }

  /**
   * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
   * and EDITOR_TAB_LIMIT, etc. values.
   */
  private inner class MyUISettingsListener : UISettingsListener {
    @RequiresEdt
    override fun uiSettingsChanged(uiSettings: UISettings) {
      mainSplitters.revalidate()
      val allSplitters = getAllSplitters()
      for (splitters in allSplitters) {
        splitters.setTabPlacement(uiSettings.editorTabPlacement)
        splitters.trimToSize()

        // Tab layout policy
        if (uiSettings.scrollTabLayoutInEditor) {
          splitters.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
        }
        else {
          splitters.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT)
        }
      }

      // "Mark modified files with asterisk"
      for (file in openedFiles.asReversed()) {
        updateFileIcon(file)
        scheduleUpdateFileName(file)
        updateFileBackgroundColor(file)
      }

      // "Show full paths in window header"
      coroutineScope.launch {
        updateFileNames(allSplitters, file = null)
        splitters.updateFrameTitle()
      }
    }
  }

  @RequiresEdt
  override fun closeAllFiles() {
    closeAllFiles(repaint = true)
  }

  @RequiresEdt
  private fun closeAllFiles(repaint: Boolean) {
    openFileSetModificationCount.increment()
    val splitters = getActiveSplitterSync()
    if (repaint) {
      runBulkTabChangeInEdt(splitters, splitters::closeAllFiles)
    }
    else {
      splitters.closeAllFiles(repaint = false)
    }
  }

  override fun closeOpenedEditors() {
    for (window in getAllSplitters().flatMap(EditorsSplitters::windows)) {
      for (file in window.files().toList()) {
        window.closeFile(file)
      }
    }
  }

  override fun getSiblings(file: VirtualFile): Collection<VirtualFile> = openedFiles

  fun queueUpdateFile(file: VirtualFile) {
    fileUpdateChannel.queue(file)
  }

  override fun getSplittersFor(component: Component): EditorsSplitters {
    val dockContainer = DockManager.getInstance(project).getContainerFor(component) { it is DockableEditorTabbedContainer }
    return if (dockContainer is DockableEditorTabbedContainer) dockContainer.splitters else mainSplitters
  }

  fun getSelectionHistory(): List<Pair<VirtualFile, EditorWindow>> = selectionHistory.getHistory().map { Pair(it.first, it.second) }

  @Internal
  fun getSelectionHistoryList(): Collection<kotlin.Pair<VirtualFile, EditorWindow>> = selectionHistory.getHistory()

  internal fun removeSelectionRecord(file: VirtualFile, window: EditorWindow) {
    selectionHistory.removeRecord(file, window)
  }

  override fun refreshIcons() {
    if (!initJob.isCompleted) {
      return
    }

    for (splitters in getAllSplitters()) {
      for (file in splitters.windows().flatMap { it.composites() }.map { it.file }.distinct()) {
        splitters.scheduleUpdateFileIcon(file)
      }
    }
  }

  internal suspend fun openFilesOnStartup(
    items: List<FileToOpen>,
    window: EditorWindow,
    requestFocus: Boolean,
    isLazyComposite: Boolean,
    windowAdded: suspend () -> Unit
  ) {
    if (items.isEmpty()) {
      LOG.warn("no files to reopen")
      return
    }

    val uiSettings = UISettings.getInstance()

    val tabs = mutableListOf<TabInfo>()
    val editorActionGroup = serviceAsync<ActionManager>().getAction("EditorTabActionGroup")

    for (item in items) {
      val fileEntry = item.fileEntry
      val file = item.file
      val composite = createCompositeByEditorWithModel(
        file = file,
        model = item.model,
        coroutineScope = item.scope,
      ) ?: continue

      if (fileEntry.currentInTab || !isLazyComposite) {
        composite.initDeferred.complete(Unit)
      }

      if (fileEntry.pinned) {
        composite.isPinned = true
      }
      else {
        if (when {
            !uiSettings.openInPreviewTabIfPossible -> false
            fileEntry.isPreview -> true
            !fileEntry.currentInTab -> false
            else -> false
          }) {
          composite.isPreview = true
        }
      }

      tabs.add(createTabInfo(
        component = composite.component,
        file = file,
        parentDisposable = composite,
        window = window,
        editorActionGroup = editorActionGroup,
        customizer = item.customizer,
      ))

      val editorCompositeEntry = EditorCompositeEntry(composite = composite, delayedState = fileEntry)
      openedCompositeEntries.add(editorCompositeEntry)
      composite.coroutineScope.launch {
        // remove delayed state as soon as composite can provide state
        composite.waitForAvailable()
        editorCompositeEntry.delayedState = null
      }
    }

    openFileSetModificationCount.increment()

    window.tabbedPane.setTabs(tabs)

    if (tabs.isEmpty()) {
      return
    }

    window.selectTabOnStartup(
      tab = tabs.get(max(items.indexOfFirst { it.fileEntry.currentInTab }, 0)),
      requestFocus = requestFocus,
      windowAdded = windowAdded,
    )

    for (tab in tabs) {
      val composite = tab.composite
      splitters.afterFileOpen(file = composite.file)
      selectionHistory.addRecord(composite.file to window)

      composite.coroutineScope.launch {
        val color = readAction { EditorTabPresentationUtil.getEditorTabBackgroundColor(composite.project, composite.file) }
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          tab.setTabColor(color)
        }
      }

      window.watchForTabActions(composite = composite, tab = tab)
    }

    items.firstOrNull { it.fileEntry.currentInTab }?.let {
      selectionHistory.addRecord(it.file to window)
    }
  }

  @Internal
  open fun forceUseUiInHeadlessMode(): Boolean = false

  @TestOnly
  fun waitForAsyncUpdateOnDumbModeFinished() {
    runBlockingMaybeCancellable {
      dumbModeFinished.emit(Unit)
      while (true) {
        UIUtil.dispatchAllInvocationEvents()
        yield()

        if (dumbModeFinished.replayCache.isEmpty()) {
          break
        }

        UIUtil.dispatchAllInvocationEvents()
        yield()
      }
    }
  }
}

@Deprecated("Please use EditorComposite directly")
open class EditorWithProviderComposite @Internal constructor(
  file: VirtualFile,
  model: Flow<EditorCompositeModel>,
  project: Project,
  coroutineScope: CoroutineScope,
) : EditorComposite(file = file, coroutineScope = coroutineScope, model = model, project = project)

private class SelectionHistory {
  private val history = ObjectLinkedOpenHashSet<kotlin.Pair<VirtualFile, EditorWindow>>()

  @Synchronized
  fun addRecord(entry: kotlin.Pair<VirtualFile, EditorWindow>) {
    history.addAndMoveToFirst(entry)
  }

  @Synchronized
  fun removeRecord(file: VirtualFile, window: EditorWindow) {
    history.remove(file to window)
  }

  @Synchronized
  fun getHistory(): Collection<kotlin.Pair<VirtualFile, EditorWindow>> {
    val copy = LinkedHashSet<kotlin.Pair<VirtualFile, EditorWindow>>()
    var modified = false
    for (pair in history) {
      val editorWindow = pair.second
      if (editorWindow.files().none()) {
        editorWindow.owner.windows().firstOrNull()?.takeIf { it.files().any() }?.let {
          copy.add(pair.first to it)
        }
        modified = true
      }
      else {
        copy.add(pair)
      }
    }
    if (modified) {
      history.clear()
      history.addAll(copy)
    }
    return copy
  }
}

private class SelectionState(
  @JvmField val composite: EditorComposite,
  @JvmField val fileEditorProvider: FileEditorWithProvider,
)

@Internal
suspend fun waitForFullyCompleted(composite: FileEditorComposite) {
  for (editor in composite.allEditors) {
    if (editor is TextEditor) {
      AsyncEditorLoader.waitForCompleted(editor.editor)
    }
  }
}

internal fun getOpenMode(event: AWTEvent): FileEditorManagerImpl.OpenMode {
  if (event is MouseEvent) {
    val isMouseClick = event.getID() == MouseEvent.MOUSE_CLICKED || event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED
    val modifiers = event.modifiersEx
    if (modifiers == InputEvent.SHIFT_DOWN_MASK && isMouseClick) {
      return FileEditorManagerImpl.OpenMode.NEW_WINDOW
    }
  }
  else if (event is KeyEvent) {
    val keymapManager = KeymapManager.getInstance()
    if (keymapManager != null) {
      @Suppress("DEPRECATION")
      val strings = keymapManager.activeKeymap.getActionIds(KeyStroke.getKeyStroke(event.keyCode, event.modifiers))
      if (strings.contains(IdeActions.ACTION_OPEN_IN_NEW_WINDOW)) {
        return FileEditorManagerImpl.OpenMode.NEW_WINDOW
      }
      if (strings.contains(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)) {
        return FileEditorManagerImpl.OpenMode.RIGHT_SPLIT
      }
    }
  }
  return FileEditorManagerImpl.OpenMode.DEFAULT
}

internal inline fun <T> runBulkTabChange(splitters: EditorsSplitters, task: () -> T): T {
  if (EDT.isCurrentThreadEdt()) {
    return runBulkTabChangeInEdt(splitters, task)
  }
  else {
    return task()
  }
}

@RequiresEdt
private inline fun <T> runBulkTabChangeInEdt(splitters: EditorsSplitters, task: () -> T): T {
  splitters.insideChange++
  try {
    return task()
  }
  finally {
    splitters.insideChange--
    if (!splitters.isInsideChange) {
      splitters.validate()
      for (window in splitters.windows()) {
        window.tabbedPane.editorTabs.revalidateAndRepaint()
      }
    }
  }
}

@RequiresEdt
fun reopenVirtualFileEditor(project: Project, oldFile: VirtualFile, newFile: VirtualFile) {
  val editorManager: FileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
  val windows: Array<EditorWindow> = editorManager.windows

  val currentWindow: EditorWindow? = if (windows.size >= 2) editorManager.currentWindow else null

  for (window in windows) {
    reopenVirtualFileInEditor(editorManager, window, oldFile, newFile)
  }

  currentWindow?.requestFocus(false)
}

private fun reopenVirtualFileInEditor(editorManager: FileEditorManagerEx, window: EditorWindow, oldFile: VirtualFile, newFile: VirtualFile) {
  val oldComposite = window.getComposite(oldFile) ?: return // the old file is not opened in this split
  val active = window.selectedComposite == oldComposite
  val pinned = window.isFilePinned(oldFile)
  var newOptions = FileEditorOpenOptions(selectAsCurrent = active, requestFocus = active, pin = pinned)

  val isSingletonEditor = window.composites().any { composite ->
    composite.allEditors.any { it.file == oldFile && isSingletonFileEditor(it) }
  }
  val dockContainer = DockManager.getInstance(editorManager.project).getContainerFor(window.component) { it is DockableEditorTabbedContainer }
  if (isSingletonEditor && dockContainer != null) {
    window.closeFile(oldFile)
    editorManager.openFile(newFile, window, newOptions.copy(openMode = FileEditorManagerImpl.OpenMode.NEW_WINDOW))
  }
  else if (oldFile == newFile) {
    val index = window.files().indexOf(oldFile)
    newOptions = newOptions.copy(index = index)
    window.closeFile(oldFile, disposeIfNeeded = false)
    editorManager.openFile(newFile, window, newOptions)
  }
  else {
    val composite = editorManager.openFile(newFile, window, newOptions)
    if (composite.allEditors.any { it.file == newFile }) {
      window.closeFile(oldFile)
    }
  }
}

@Suppress("SSBasedInspection")
@RequiresEdt
@Internal
fun blockingWaitForCompositeFileOpen(composite: EditorComposite) {
  ThreadingAssertions.assertEventDispatchThread()

  val job = composite.coroutineScope.launch {
    composite.waitForAvailable()
  }

  // https://youtrack.jetbrains.com/issue/IDEA-319932
  // runWithModalProgressBlocking cannot be used under a write action - https://youtrack.jetbrains.com/issue/IDEA-319932
  if (ApplicationManager.getApplication().isWriteAccessAllowed) {
    // todo silenceWriteLock instead of executeSuspendingWriteAction
    (ApplicationManager.getApplication() as ApplicationImpl).executeSuspendingWriteAction(
      composite.project,
      EditorBundle.message("editor.open.file.progress", composite.file.name),
    ) {
      runBlockingMaybeCancellable {
        job.join()
      }
    }
  }
  else {
    // we don't need progress - handled by async editor loader
    runBlocking {
      job.invokeOnCompletion {
        EventQueue.invokeLater(EmptyRunnable.getInstance())
      }

      IdeEventQueue.getInstance().pumpEventsForHierarchy {
        job.isCompleted
      }
    }
  }
}

private suspend fun updateFileNames(allSplitters: Set<EditorsSplitters>, file: VirtualFile?) {
  for (splitters in allSplitters) {
    for (window in splitters.windows()) {
      val composites = withContext(Dispatchers.EDT) { window.composites() }
      for (composite in composites) {
        // update names for other files with the same name, as it might affect UniqueNameEditorTabTitleProvider
        if (file != null && composite.file != file && !composite.file.nameSequence.contentEquals(file.nameSequence)) {
          continue
        }

        val title = EditorTabPresentationUtil.getEditorTabTitleAsync(splitters.manager.project, composite.file)
        withContext(Dispatchers.EDT) {
          val tab = window.findTabByComposite(composite) ?: return@withContext
          tab.setText(title)
          tab.setTooltipText(if (UISettings.getInstance().showTabsTooltips) splitters.manager.getFileTooltipText(composite.file, composite) else null)
        }
      }
    }
  }
}

internal fun isSingletonFileEditor(fileEditor: FileEditor?): Boolean = SINGLETON_EDITOR_IN_WINDOW.get(fileEditor, false)

@Internal
fun getForegroundColorForFile(project: Project, file: VirtualFile): ColorKey? {
  return EditorTabColorProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.getEditorTabForegroundColor(project, file)
  }
}

@Internal
fun navigateAndSelectEditor(editor: NavigatableFileEditor, descriptor: Navigatable, composite: EditorComposite?): Boolean {
  if (editor.canNavigateTo(descriptor)) {
    composite?.setSelectedEditor(editor)
    editor.navigateTo(descriptor)
    return true
  }
  return false
}

private fun getEditorTypeIds(composite: EditorComposite): Set<String> {
  return composite.providerSequence.mapTo(HashSet()) { it.editorTypeId }
}

private data class ProviderChange(
  @JvmField val composite: EditorComposite,
  @JvmField val newProviders: List<FileEditorProvider>,
  @JvmField val editorTypeIdsToRemove: List<String>,
)
