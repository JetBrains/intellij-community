// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION", "ReplaceGetOrSet", "LeakingThis")
@file:OptIn(FlowPreview::class, FlowPreview::class, FlowPreview::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.ProjectTopics
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.*
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.SplitAction
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector
import com.intellij.lang.LangBundle
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorComposite.Companion.retrofit
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.*
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
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.pom.Navigatable
import com.intellij.ui.ComponentUtil
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.impl.DockManagerImpl
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ExceptionUtil
import com.intellij.util.IconUtil
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.toArray
import com.intellij.util.flow.zipWithNext
import com.intellij.util.messages.impl.MessageListenerList
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<FileEditorManagerImpl>()

@OptIn(ExperimentalCoroutinesApi::class)
@State(name = "FileEditorManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
open class FileEditorManagerImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : FileEditorManagerEx(), PersistentStateComponent<Element?>, Disposable {
  private val dumbModeFinishedScope = coroutineScope.childScope()

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
    DockableEditorTabbedContainer(splitters = mainSplitters, disposeWhenEmpty = false, coroutineScope = coroutineScope)
  }

  private val selectionHistory = SelectionHistory()

  private val fileUpdateChannel: MergingUpdateChannel<VirtualFile> = MergingUpdateChannel(delay = 50.milliseconds) { toUpdate ->
    val fileDocumentManager = FileDocumentManager.getInstance()
    for (file in toUpdate) {
      if (fileDocumentManager.isFileModified(file)) {
        for (composite in openedComposites.filter { it.file == file }) {
          withContext(Dispatchers.EDT) {
            if (composite.isPreview) {
              composite.isPreview = false
            }
          }
        }
      }

      for (each in getAllSplitters()) {
        each.doUpdateFileIcon(file)
        each.updateFileColor(file)
        each.updateFileBackgroundColor(file)
      }
    }
  }

  private val fileTitleUpdateChannel: MergingUpdateChannel<VirtualFile?> = MergingUpdateChannel(delay = 50.milliseconds) { toUpdate ->
    val splitters = withContext(Dispatchers.EDT) { getAllSplitters() }
    for (file in toUpdate) {
      for (each in splitters) {
        each.updateFileName(file)
      }
    }
  }

  /**
   * Removes invalid editor and updates "modified" status.
   */
  private val editorPropertyChangeListener = MyEditorPropertyChangeListener()

  /**
   * Updates file tooltip
   */
  private val editorCompositeListener = MyEditorCompositeListener()

  private var contentFactory: DockableEditorContainerFactory? = null
  private val openedComposites = CopyOnWriteArrayList<EditorComposite>()
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

    val selectionFlow: StateFlow<SelectionState?> = splitterFlow
      .flatMapLatest { it.currentCompositeFlow }
      .flatMapLatest { composite ->
        if (composite == null) {
          return@flatMapLatest flowOf(null)
        }

        fileTitleUpdateChannel.queue(composite.file)
        composite.selectedEditorWithProvider.mapLatest { fileEditorWithProvider ->
          if (fileEditorWithProvider == null) null else SelectionState(composite = composite, fileEditorProvider = fileEditorWithProvider)
        }
      }
      .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val publisher = project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)

    // not using collectLatest() to ensure that the listeners miss no selection update
    selectionFlow
      .zipWithNext { oldState, state ->
        val oldEditorWithProvider = oldState?.fileEditorProvider
        val newEditorWithProvider = state?.fileEditorProvider
        if (oldEditorWithProvider == newEditorWithProvider) {
          return@zipWithNext
        }

        // expected in EDT
        withContext(Dispatchers.EDT) {
          blockingContext {
            kotlin.runCatching {
              fireSelectionChanged(newComposite = state?.composite,
                                   oldEditorWithProvider = oldEditorWithProvider,
                                   newEditorWithProvider = newEditorWithProvider,
                                   publisher = publisher)
            }.getOrLogException(LOG)
          }
        }
      }
      .launchIn(coroutineScope)

    currentFileEditorFlow = selectionFlow
      .map { it?.fileEditorProvider?.fileEditor }
      .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    project.messageBus.connect(coroutineScope).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        dumbModeFinishedScope.launch {
          dumbModeFinished(project)
        }
      }
    })
    closeFilesOnFileEditorRemoval()

    if (ApplicationManager.getApplication().isUnitTestMode || forceUseUiInHeadlessMode()) {
      initJob = CompletableDeferred(value = Unit)
      mainSplitters = EditorsSplitters(manager = this, coroutineScope = coroutineScope)
      check(splitterFlow.tryEmit(mainSplitters))
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
      connection.subscribe(ProjectTopics.PROJECT_ROOTS, MyRootListener())
      connection.subscribe(AdditionalLibraryRootsListener.TOPIC, MyRootListener())
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

    processFileUpdateRequests()
  }

  @RequiresEdt
  internal suspend fun init(): kotlin.Pair<EditorsSplitters, EditorSplitterState?> {
    initJob.join()
    return mainSplitters to state.getAndSet(null)
  }

  companion object {
    @JvmField
    protected val DUMB_AWARE: Key<Boolean> = Key.create("DUMB_AWARE")

    @JvmField
    val NOTHING_WAS_OPENED_ON_START: Key<Boolean> = Key.create("NOTHING_WAS_OPENED_ON_START")

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

    fun isDumbAware(editor: FileEditor): Boolean {
      return true == editor.getUserData(DUMB_AWARE) && (editor !is PossiblyDumbAware || (editor as PossiblyDumbAware).isDumbAware)
    }

    private fun isFileOpenInWindow(file: VirtualFile, window: EditorWindow): Boolean {
      val shouldFileBeSelected = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE
      return if (shouldFileBeSelected) file == window.selectedFile else window.isFileOpen(file)
    }

    @JvmStatic
    fun forbidSplitFor(file: VirtualFile): Boolean = file.getUserData(SplitAction.FORBID_TAB_SPLIT) == true

    @JvmStatic
    internal fun isSingletonFileEditor(fileEditor: FileEditor?): Boolean = SINGLETON_EDITOR_IN_WINDOW.get(fileEditor, false)

    internal fun getOriginalFile(file: VirtualFile): VirtualFile {
      return BackedVirtualFile.getOriginFileIfBacked(if (file is VirtualFileWindow) file.delegate else file)
    }
  }

  protected open fun canOpenFile(file: VirtualFile, providers: List<FileEditorProvider>): Boolean = !providers.isEmpty()

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
          for (provider in editor.allProviders) {
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
      if (dockable.isInitialized()) {
        Disposer.dispose(dockable.value)
      }

      for (composite in openedComposites) {
        Disposer.dispose(composite)
      }
    }
  }

  // need to open additional non-dumb-aware editors
  private suspend fun dumbModeFinished(project: Project) {
    val allSplitters = withContext(Dispatchers.EDT) {
      getAllSplitters() to openedFiles
    }

    val providerManager = FileEditorProviderManager.getInstance()
    // predictable order of iteration
    val fileToNewProviders = openedComposites.groupByTo(LinkedHashMap()) { it.file }.entries.mapNotNull { entry ->
      val composites = entry.value
      val existingIds = composites.asSequence().flatMap(EditorComposite::providerSequence).mapTo(HashSet()) { it.editorTypeId }
      val file = entry.key
      val newProviders = providerManager.getProvidersAsync(project, file).filter { !existingIds.contains(it.editorTypeId) }
      if (newProviders.isEmpty()) {
        null
      }
      else {
        file to composites.map { composite -> composite to newProviders.map { it to readAction { it.createEditor(project, file) } } }
      }
    }
    for ((file, toOpen) in fileToNewProviders) {
      withContext(Dispatchers.EDT) {
        for ((composite, providerAndEditors) in toOpen) {
          for ((provider, editor) in providerAndEditors) {
            composite.addEditor(editor = editor, provider = provider)
          }
        }
        for (each in allSplitters.first) {
          each.updateFileBackgroundColorAsync(file)
        }
      }
    }

    // update for non-dumb-aware EditorTabTitleProviders
    updateFileName(file = null)
  }

  @RequiresEdt
  fun initDockableContentFactory() {
    if (contentFactory != null) {
      return
    }
    contentFactory = DockableEditorContainerFactory(this)
    DockManager.getInstance(project).register(DockableEditorContainerFactory.TYPE, contentFactory!!, this)
  }

  override val component: JComponent?
    get() = mainSplitters

  fun getAllSplitters(): Set<EditorsSplitters> {
    val all = LinkedHashSet<EditorsSplitters>()
    all.add(mainSplitters)
    for (container in DockManager.getInstance(project).containers) {
      if (container is DockableEditorTabbedContainer) {
        all.add(container.splitters)
      }
    }
    return all
  }

  private fun getActiveSplittersAsync(): Deferred<EditorsSplitters?> {
    val result = CompletableDeferred<EditorsSplitters?>()
    val focusManager = IdeFocusManager.getGlobalInstance()
    focusManager.doWhenFocusSettlesDown {
      if (project.isDisposed) {
        result.complete(null)
        return@doWhenFocusSettlesDown
      }

      val container = DockManager.getInstance(project).getContainerFor(focusManager.focusOwner) {
        it is DockableEditorTabbedContainer
      }
      result.complete(if (container is DockableEditorTabbedContainer) container.splitters else mainSplitters)
    }
    return result
  }

  @RequiresEdt
  private fun getActiveSplitterSync(): EditorsSplitters {
    if (Registry.`is`("ide.navigate.to.recently.focused.editor", false)) {
      getLastFocusedSplitters()?.let { return it }
    }

    val focusManager = IdeFocusManager.getGlobalInstance()
    var focusOwner = focusManager.focusOwner
                     ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                     ?: focusManager.getLastFocusedFor(focusManager.lastFocusedIdeWindow)
    var container = DockManager.getInstance(project).getContainerFor(focusOwner) { it is DockableEditorTabbedContainer }
    if (container == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
      container = DockManager.getInstance(project).getContainerFor(focusOwner) { it is DockableEditorTabbedContainer }
    }
    return if (container is DockableEditorTabbedContainer) container.splitters else mainSplitters
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

  open fun getFileTooltipText(file: VirtualFile, window: EditorWindow): @NlsContexts.Tooltip String {
    val composite = window.getComposite(file)
    val prefix = if (composite != null && composite.isPreview) "${LangBundle.message("preview.editor.tab.tooltip.text")} " else ""
    for (provider in EditorTabTitleProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      val text = provider.getEditorTabTooltipText(project, file)
      if (text != null) {
        return prefix + text
      }
    }
    return prefix + FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
  }

  override fun updateFilePresentation(file: VirtualFile) {
    if (!isFileOpen(file)) {
      return
    }
    updateFileName(file)
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

    for (each in getAllSplitters()) {
      each.updateFileColorAsync(file)
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
    for (each in getAllSplitters()) {
      if (immediately) {
        each.updateFileIconImmediately(file, IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, project))
      }
      else {
        each.updateFileIcon(file)
      }
    }
  }

  /**
   * Updates tab title and tab tool tip for the specified `file`.
   */
  private fun updateFileName(file: VirtualFile?) {
    // Queue here is to prevent title flickering when the tab is being closed and two events are arriving:
    // with component==null and component==next focused tab
    // only the last event makes sense to handle
    fileTitleUpdateChannel.queue(file)
  }

  override fun unsplitWindow() {
    getActiveSplitterSync().currentWindow?.unsplit(true)
  }

  override fun unsplitAllWindow() {
    getActiveSplitterSync().currentWindow?.unsplitAll()
  }

  override val windowSplitCount: Int
    get() = getActiveSplitterSync().splitCount

  override fun hasSplitOrUndockedWindows(): Boolean = getAllSplitters().size > 1 || windowSplitCount > 1

  override val windows: Array<EditorWindow>
    get() = getAllSplitters().flatMap(EditorsSplitters::getWindowSequence).toTypedArray()

  override fun getNextWindow(window: EditorWindow): EditorWindow? {
    val windows = splitters.getOrderedWindows()
    for (i in windows.indices) {
      if (windows[i] == window) {
        return windows.get((i + 1) % windows.size)
      }
    }
    LOG.error("No window found")
    return null
  }

  override fun getPrevWindow(window: EditorWindow): EditorWindow? {
    val windows = splitters.getOrderedWindows()
    for (i in windows.indices) {
      if (windows[i] == window) {
        return windows.get((i + windows.size - 1) % windows.size)
      }
    }
    LOG.error("No window found")
    return null
  }

  override fun createSplitter(orientation: Int, window: EditorWindow?) {
    // the window was available from action event, for example, when invoked from the tab menu of an editor that is not the 'current'
    if (window != null) {
      window.split(orientation = orientation, forceSplit = true, virtualFile = null, focusNew = false)
    }
    else {
      splitters.currentWindow?.split(orientation = orientation, forceSplit = true, virtualFile = null, focusNew = false)
    }
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
    window.closeFile(file = file, composite = composite)
    removeSelectionRecord(file, window)
    return true
  }

  /**
   * Runs pre-close checks on virtual file
   * @return true if all the checks were successfully passed and the file can be closed
   */
  private fun canCloseFile(file: VirtualFile): Boolean {
    val checks = VirtualFilePreCloseCheck.extensionPoint.extensionsIfPointIsRegistered
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
    if (!closeAllCopies) {
      if (ClientId.isCurrentlyUnderLocalId) {
        openFileSetModificationCount.increment()
        val activeSplitters = getActiveSplitterSync()
        runBulkTabChangeInEdt(activeSplitters) { activeSplitters.closeFile(file, moveFocus) }
      }
      else {
        clientFileEditorManager?.closeFile(file, false)
      }
    }
    else {
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
  }

  private fun closeFileEditor(editor: FileEditor, moveFocus: Boolean = true) {
    val file = editor.file ?: return
    if (ClientId.isCurrentlyUnderLocalId) {
      openFileSetModificationCount.increment()
      for (each in getAllSplitters()) {
        runBulkTabChange(each) { each.closeFileEditor(file, editor, moveFocus) }
      }
    }
    else {
      clientFileEditorManager?.closeFile(file, false) // todo pass editor inside ?
    }
  }

  private val allClientFileEditorManagers: List<ClientFileEditorManager>
    get() = project.getServices(ClientFileEditorManager::class.java, ClientKind.REMOTE)

  override fun isFileOpenWithRemotes(file: VirtualFile): Boolean {
    return isFileOpen(file) || allClientFileEditorManagers.any { it.isFileOpen(file) }
  }

  override fun openFile(file: VirtualFile, window: EditorWindow?, options: FileEditorOpenOptions): FileEditorComposite {
    require(file.isValid) { "file is not valid: $file" }

    var windowToOpenIn = window
    if (windowToOpenIn != null && windowToOpenIn.isDisposed) {
      windowToOpenIn = null
    }

    if (windowToOpenIn == null) {
      val mode = getOpenMode(IdeEventQueue.getInstance().trueCurrentEvent)
      if (mode == OpenMode.NEW_WINDOW) {
        if (forbidSplitFor(file)) {
          closeFile(file)
        }
        return (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(file) { editorWindow ->
          openFileImpl2(window = editorWindow, file = file, options = options)
        }
      }
      else if (mode == OpenMode.RIGHT_SPLIT) {
        openInRightSplit(file)?.let {
          return it
        }
      }
    }

    if (windowToOpenIn == null && (options.reuseOpen || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER))) {
      windowToOpenIn = findWindowInAllSplitters(file)
    }
    if (windowToOpenIn == null) {
      windowToOpenIn = getOrCreateCurrentWindow(file)
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      if (forbidSplitFor(file = file) && !windowToOpenIn.isFileOpen(file)) {
        closeFile(file)
      }
      return openFileImpl4(window = windowToOpenIn, _file = file, entry = null, options = options)
    }
    else {
      val context = ClientId.coroutineContext()
      return runWithModalProgressBlocking(project, EditorBundle.message("editor.open.file.progress", file.name)) {
        withContext(context) {
          openFileAsync(window = windowToOpenIn, file = getOriginalFile(file), entry = null, options = options)
        }
      }
    }
  }

  override suspend fun openFile(file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    var windowToOpenIn: EditorWindow? = null
    if (options.reuseOpen || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)) {
      windowToOpenIn = withContext(Dispatchers.EDT) { findWindowInAllSplitters(file) }
    }
    if (windowToOpenIn == null) {
      windowToOpenIn = withContext(Dispatchers.EDT) { getOrCreateCurrentWindow(file) }
    }
    if (forbidSplitFor(file) && !windowToOpenIn.isFileOpen(file)) {
      withContext(Dispatchers.EDT) {
        closeFile(file)
      }
    }
    return openFileAsync(window = windowToOpenIn, file = getOriginalFile(file), entry = null, options = options)
  }

  private fun findWindowInAllSplitters(file: VirtualFile): EditorWindow? {
    val activeCurrentWindow = getActiveSplitterSync().currentWindow
    if (activeCurrentWindow != null && isFileOpenInWindow(file, activeCurrentWindow)) {
      return activeCurrentWindow
    }

    for (splitters in getAllSplitters()) {
      for (window in splitters.getWindows()) {
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

  private fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val currentEditor = selectedEditor
    val isSingletonEditor = isSingletonFileEditor(currentEditor)
    val currentWindow = splitters.currentWindow

    // If the selected editor is a singleton in a split window, prefer the sibling of that split window.
    // When navigating from a diff view, opened in a vertical split,
    // this makes a new tab open below/above the diff view, still keeping the diff in sight.
    if (isSingletonEditor && currentWindow != null && currentWindow.inSplitter() &&
        currentWindow.tabCount == 1 && currentWindow.selectedComposite?.selectedEditor === currentEditor) {
      val siblingWindow = currentWindow.getSiblings().firstOrNull()
      if (siblingWindow != null) {
        return siblingWindow
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
    if (forbidSplitFor(file)) {
      closeFile(file)
    }
    return (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(file) { editorWindow ->
      openFileImpl2(editorWindow, file, FileEditorOpenOptions(requestFocus = true))
    }.retrofit()
  }

  private fun openInRightSplit(file: VirtualFile): FileEditorComposite? {
    val active = splitters
    val window = active.currentWindow
    if (window == null || window.inSplitter() && file == window.selectedFile && file == window.getFileSequence().lastOrNull()) {
      // already in right splitter
      return null
    }

    val split = active.openInRightSplit(file) ?: return null
    val editorsWithProviders = split.getComposites().flatMap(EditorComposite::allEditorsWithProviders).toList()
    return FileEditorComposite.createFileEditorComposite(allEditors = editorsWithProviders.map { it.fileEditor },
                                                         allProviders = editorsWithProviders.map { it.provider })
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use public API.", level = DeprecationLevel.ERROR)
  fun openFileImpl2(window: EditorWindow,
                    file: VirtualFile,
                    focusEditor: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileImpl2(window = window, file = file, options = FileEditorOpenOptions(requestFocus = focusEditor)).retrofit()
  }

  open fun openFileImpl2(window: EditorWindow,
                         file: VirtualFile,
                         options: FileEditorOpenOptions): FileEditorComposite {
    if (forbidSplitFor(file) && !window.isFileOpen(file)) {
      closeFile(file)
    }
    return openFileImpl4(window = window, _file = file, entry = null, options = options)
  }

  /**
   * Unlike the openFile method, file can be invalid.
   * For example, all files were invalidated, and they're being removed one by one.
   * If we've removed one invalid file, then another invalid file becomes selected.
   * That's why we don't require that passed file is valid.
   */
  internal fun openMaybeInvalidFile(window: EditorWindow, file: VirtualFile, entry: HistoryEntry?): FileEditorComposite {
    return openFileImpl4(window = window, _file = file, entry = entry, options = FileEditorOpenOptions(requestFocus = true))
  }

  private val clientFileEditorManager: ClientFileEditorManager?
    get() {
      val clientId = ClientId.current
      LOG.assertTrue(!clientId.isLocal, "Trying to get ClientFileEditorManager for local ClientId")
      return ClientSessionsManager.getProjectSession(project, clientId)?.serviceOrNull<ClientFileEditorManager>()
    }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  @Suppress("DuplicatedCode")
  internal fun openFileImpl4(window: EditorWindow,
                             @Suppress("LocalVariableName") _file: VirtualFile,
                             entry: HistoryEntry?,
                             options: FileEditorOpenOptions): FileEditorComposite {
    assert(ApplicationManager.getApplication().isDispatchThread ||
           !ApplicationManager.getApplication().isReadAccessAllowed) { "must not attempt opening files under read action" }
    val file = getOriginalFile(_file)
    if (!ClientId.isCurrentlyUnderLocalId) {
      return openFileUsingClient(file, options)
    }

    val existingComposite = if (EDT.isCurrentThreadEdt()) {
      window.getComposite(file)
    }
    else {
      runBlockingCancellable { withContext(Dispatchers.EDT) { window.getComposite(file) } }
    }

    val newProviders: List<kotlin.Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>
    if (existingComposite == null) {
      if (!canOpenFile(file)) {
        return FileEditorComposite.EMPTY
      }

      // A file is not opened yet. In this case, we have to create editors and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviderList(project, file).map { provider ->
        provider to if (provider is AsyncFileEditorProvider) {
          ApplicationManager.getApplication().runReadAction<AsyncFileEditorProvider.Builder?, RuntimeException> {
            provider.createEditorAsync(project, file)
          }
        }
        else {
          null
        }
      }
    }
    else {
      newProviders = emptyList()
    }

    val effectiveOptions = getEffectiveOptions(options, entry)

    fun open(): FileEditorComposite {
      return runBulkTabChange(window.owner) {
        (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
        LOG.assertTrue(file.isValid, "Invalid file: $file")
        doOpenInEdtImpl(
          existingComposite = existingComposite,
          window = window,
          file = file,
          entry = entry,
          options = effectiveOptions,
          newProviders = newProviders)
      }
    }

    if (EDT.isCurrentThreadEdt()) {
      return open()
    }
    else {
      return runBlockingCancellable {
        withContext(Dispatchers.EDT) {
          open()
        }
      }
    }
  }

  @Suppress("DuplicatedCode")
  private suspend fun openFileAsync(window: EditorWindow,
                                    file: VirtualFile,
                                    entry: HistoryEntry?,
                                    options: FileEditorOpenOptions): FileEditorComposite {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.openFileAsync(file = file,
                                                    forceCreate = false,
                                                    requestFocus = options.requestFocus) ?: FileEditorComposite.EMPTY
    }

    val existingComposite = withContext(Dispatchers.EDT) { window.getComposite(file) }
    return coroutineScope {
      val providers: List<kotlin.Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>
      if (existingComposite == null) {
        if (!canOpenFileAsync(file)) {
          return@coroutineScope FileEditorComposite.EMPTY
        }

        // A file is not opened yet. In this case, we have to create editors and select the created EditorComposite.
        val p = FileEditorProviderManager.getInstance().getProvidersAsync(project, file)
        val builders = createBuilders(providers = p, file = file, project = project)
        providers = p.mapIndexed { index, provider -> provider to builders.get(index)?.await() }
      }
      else {
        providers = emptyList()
      }

      val effectiveOptions = getEffectiveOptions(options, entry)
      openFileInEdt(existingComposite = existingComposite,
                    window = window,
                    file = file,
                    entry = entry,
                    options = effectiveOptions,
                    providerWithBuilderList = providers)
    }
  }

  private fun openFileUsingClient(file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    val clientManager = clientFileEditorManager ?: return FileEditorComposite.EMPTY
    return clientManager.openFile(file = file, forceCreate = false, requestFocus = options.requestFocus)
  }

  @Suppress("DuplicatedCode")
  private fun doOpenInEdtImpl(
    existingComposite: EditorComposite?,
    window: EditorWindow,
    file: VirtualFile,
    entry: HistoryEntry?,
    options: FileEditorOpenOptions,
    newProviders: List<kotlin.Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>?,
  ): FileEditorComposite {
    val startTime = System.nanoTime()

    var composite = existingComposite
    val newEditor = composite == null
    if (newEditor) {
      composite = createComposite(file = file, providers = newProviders!!) ?: return FileEditorComposite.EMPTY
      project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file)
      openedComposites.add(composite)
    }

    val editorsWithProviders = composite!!.allEditorsWithProviders
    window.setComposite(composite, options)
    for (editorWithProvider in editorsWithProviders) {
      restoreEditorState(file = file,
                         editorWithProvider = editorWithProvider,
                         entry = entry,
                         newEditor = newEditor,
                         exactState = options.isExactState)
    }

    // restore selected editor
    val provider = if (entry == null) {
      FileEditorProviderManagerImpl.getInstanceImpl().getSelectedFileEditorProvider(composite, project)
    }
    else {
      entry.selectedProvider
    }
    if (provider != null) {
      composite.setSelectedEditor(provider.editorTypeId)
    }

    // notify editors about selection changes
    val splitters = window.owner
    splitters.setCurrentWindow(window = window, requestFocus = false)

    splitters.afterFileOpen(file)
    addSelectionRecord(file, window)
    val selectedEditor = composite.selectedEditor
    selectedEditor?.selectNotify()

    if (newEditor) {
      openFileSetModificationCount.increment()
    }

    // update frame and tab title
    updateFileName(file)

    if (options.pin) {
      window.setFilePinned(composite, pinned = true)
    }

    // transfer focus into editor
    if (options.requestFocus && !ApplicationManager.getApplication().isUnitTestMode) {
      if (selectedEditor is TextEditor) {
        runWhenLoaded(selectedEditor.editor) {
          // while the editor was loading asynchronously, the user switched to another editor - don't steal focus
          if (splitters.currentWindow === window && window.selectedComposite === composite) {
            composite.preferredFocusedComponent?.requestFocusInWindow()
            IdeFocusManager.getGlobalInstance().toFront(splitters)
          }
        }

      }
      else {
        composite.preferredFocusedComponent?.requestFocusInWindow()
        IdeFocusManager.getGlobalInstance().toFront(splitters)
      }
    }

    if (existingComposite == null) {
      val messageBus = project.messageBus
      messageBus.syncPublisher(FileOpenedSyncListener.TOPIC).fileOpenedSync(this, file, editorsWithProviders)
      @Suppress("DEPRECATION")
      messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpenedSync(this, file, editorsWithProviders)
      triggerOpen(project = project, file = file, start = startTime, composite = composite)
      coroutineScope.launch(Dispatchers.EDT) {
        if (isFileOpen(file)) {
          project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(this@FileEditorManagerImpl, file)
        }
      }
    }
    return composite
  }

  protected open fun createComposite(file: VirtualFile,
                                     providers: List<kotlin.Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>): EditorComposite? {
    val editorsWithProviders = providers.mapNotNull { (provider, builder) ->
      runCatching {
        val editor = builder?.build() ?: provider.createEditor(project, file)
        if (editor.isValid) {
          FileEditorWithProvider(editor, provider)
        }
        else {
          val pluginDescriptor = PluginManager.getPluginByClass(provider.javaClass)
          LOG.error(PluginException("Invalid editor created by provider ${provider.javaClass.name}", pluginDescriptor?.pluginId))
          null
        }
      }.getOrLogException(LOG)
    }

    if (editorsWithProviders.isEmpty()) {
      return null
    }
    return createCompositeByEditorWithProviderList(file = file, editorsWithProviders = editorsWithProviders)
  }

  protected fun createCompositeByEditorWithProviderList(file: VirtualFile,
                                                        editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      editor.addPropertyChangeListener(editorPropertyChangeListener)
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
    }

    val composite = createCompositeInstance(file, editorsWithProviders) ?: return null
    composite.addListener(editorCompositeListener, disposable = this)
    return composite
  }

  protected fun createCompositeInstance(file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    if (ClientId.isCurrentlyUnderLocalId) {
      // the only place this class is created, won't be needed when we get rid of EditorWithProviderComposite usages
      @Suppress("DEPRECATION")
      return EditorWithProviderComposite(file = file, editorsWithProviders = editorsWithProviders, project = project)
    }
    else {
      return clientFileEditorManager?.createComposite(file, editorsWithProviders)
    }
  }

  private fun restoreEditorState(file: VirtualFile,
                                 editorWithProvider: FileEditorWithProvider,
                                 entry: HistoryEntry?,
                                 newEditor: Boolean,
                                 exactState: Boolean) {
    var state: FileEditorState? = null
    val provider = editorWithProvider.provider
    if (entry != null) {
      state = entry.getState(provider)
    }
    if (state == null && newEditor) {
      // We have to try to get state from the history only in case of the editor is not opened.
      // Otherwise, history entry might have a state out of sync with the current editor state.
      state = EditorHistoryManager.getInstance(project).getState(file, provider)
    }
    if (state != null) {
      val editor = editorWithProvider.fileEditor
      if (isDumbAware(editor)) {
        editor.setState(state, exactState)
      }
      else {
        DumbService.getInstance(project).runWhenSmart { editor.setState(state, exactState) }
      }
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

  fun newEditorComposite(file: VirtualFile): EditorComposite? {
    if (!canOpenFile(file)) {
      return null
    }

    val providers = FileEditorProviderManager.getInstance().getProviderList(project, file)
    val newComposite = createComposite(file = file, providers = providers.map { it to null }) ?: return null
    val editorHistoryManager = EditorHistoryManager.getInstance(project)
    for (editorWithProvider in newComposite.allEditorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      val provider = editorWithProvider.provider

      // restore myEditor state
      val state = editorHistoryManager.getState(file, provider)
      if (state != null) {
        editor.setState(state)
      }
    }
    return newComposite
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
    )
    val result = if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      // runWithModalProgressBlocking cannot be used under a write action - https://youtrack.jetbrains.com/issue/IDEA-319932
      openFile(file = file, window = null, options = openOptions).allEditors
    }
    else {
      val context = ClientId.coroutineContext()
      runWithModalProgressBlocking(project, EditorBundle.message("editor.open.file.progress", file.name)) {
        withContext(context) {
          openFile(file = file, options = openOptions).allEditors
        }
      }
    }

    for (editor in result) {
      // try to navigate opened editor
      if (editor is NavigatableFileEditor && getSelectedEditor(effectiveDescriptor.file) === editor) {
        if (navigateAndSelectEditor(editor, effectiveDescriptor)) {
          return result to editor
        }
      }
    }

    for (editor in result) {
      // try other editors
      if (editor is NavigatableFileEditor && getSelectedEditor(effectiveDescriptor.file) !== editor) {
        if (navigateAndSelectEditor(editor, effectiveDescriptor)) {
          return result to editor
        }
      }
    }
    return result to null
  }

  private fun navigateAndSelectEditor(editor: NavigatableFileEditor, descriptor: Navigatable): Boolean {
    if (editor.canNavigateTo(descriptor)) {
      setSelectedEditor(editor)
      editor.navigateTo(descriptor)
      return true
    }
    return false
  }

  private fun setSelectedEditor(editor: FileEditor) {
    (getComposite(editor) ?: return).setSelectedEditor(editor)
  }

  override fun getProject(): Project = project

  override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? {
    val editorsWithSelected = openEditorImpl(descriptor = descriptor, focusEditor = focusEditor)
    val fileEditors = editorsWithSelected.first
    val selectedEditor = editorsWithSelected.second
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
    setSelectedEditor(target)
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

  fun getSelectedTextEditor(isLockFree: Boolean): Editor? {
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
    val result = openedFiles.toMutableList()
    for (m in allClientFileEditorManagers) {
      result.addAll(m.getAllFiles())
    }
    return result
  }

  override fun hasOpenFiles(): Boolean = !openedComposites.isEmpty()

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
        splitters.sortWith(Comparator { o1, o2 ->
          o2.lastFocusGainedTime.compareTo(o1.lastFocusGainedTime)
        })
        return splitters[0]
      }
    }
    return null
  }

  override fun getSelectedEditor(): FileEditor? {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditor()
    }
    if (!initJob.isCompleted) {
      return null
    }

    val selected = splitters.currentWindow?.selectedComposite
    return selected?.selectedEditor ?: super.getSelectedEditor()
  }

  @RequiresEdt
  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? = getComposite(file)?.selectedWithProvider

  @RequiresEdt
  override fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return retrofit(getComposite(file))
  }

  @RequiresEdt
  final override fun getEditors(file: VirtualFile): Array<FileEditor> = getComposite(file)?.allEditors?.toTypedArray() ?: FileEditor.EMPTY_ARRAY

  final override fun getEditorList(file: VirtualFile): List<FileEditor> = getComposite(file)?.allEditors ?: emptyList()

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> {
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
    return result.toTypedArray()
  }

  @RequiresEdt
  override fun getComposite(file: VirtualFile): EditorComposite? {
    val originalFile = getOriginalFile(file)
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getComposite(originalFile)
    }

    if (openedComposites.isEmpty()) {
      return null
    }

    return splitters.currentWindow?.getComposite(originalFile)
           ?: openedComposites.firstOrNull { it.file == originalFile }
  }

  fun getAllComposites(file: VirtualFile): List<EditorComposite> {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getAllComposites(file) ?: emptyList()
    }
    return getAllSplitters().flatMap { it.getAllComposites(file) }
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
    mainSplitters.writeExternal(state)
    return state
  }

  override fun loadState(state: Element) {
    this.state.set(EditorSplitterState(state))
  }

  open fun getComposite(editor: FileEditor): EditorComposite? {
    return openedComposites.asReversed().firstOrNull { it.containsFileEditor(editor) }
           ?: allClientFileEditorManagers.firstNotNullOfOrNull { it.getComposite(editor) }
  }

  private fun fireSelectionChanged(newComposite: EditorComposite?,
                                   oldEditorWithProvider: FileEditorWithProvider?,
                                   newEditorWithProvider: FileEditorWithProvider?,
                                   publisher: FileEditorManagerListener) {
    val task = {
      oldEditorWithProvider?.fileEditor?.deselectNotify()

      val newEditor = newEditorWithProvider?.fileEditor
      if (newEditor != null) {
        newEditor.selectNotify()
        FileEditorCollector.logAlternativeFileEditorSelected(project, newComposite!!.file, newEditor)
        (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).providerSelected(newComposite)
      }
      IdeDocumentHistory.getInstance(project).onSelectionChanged()
    }

    task()

    val newFile = newComposite?.file
    if (newFile != null) {
      val holder = ComponentUtil.getParentOfType(EditorWindowHolder::class.java, newComposite.component)
      if (holder != null) {
        addSelectionRecord(file = newFile, window = holder.editorWindow)
      }
    }

    publisher.selectionChanged(FileEditorManagerEvent(manager = this,
                                                      oldEditorWithProvider = oldEditorWithProvider,
                                                      newEditorWithProvider = newEditorWithProvider))
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

    openedComposites.remove(composite)
    composite.deselectNotify()
    splitters.onDisposeComposite(composite)

    for (editorWithProvider in composite.allEditorsWithProviders.asReversed()) {
      val editor = editorWithProvider.fileEditor
      editor.removePropertyChangeListener(editorPropertyChangeListener)
      editorWithProvider.provider.disposeEditor(editor)
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
          updateFileName(file)
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
          updateFileName(openFile)
          updateFileBackgroundColor(openFile)
        }
      }
    }
  }

  private inner class MyEditorPropertyChangeListener : PropertyChangeListener {
    @RequiresEdt
    override fun propertyChange(e: PropertyChangeEvent) {
      if (project.isDisposed) return
      val propertyName = e.propertyName
      if (FileEditor.PROP_MODIFIED == propertyName) {
        val editor = e.source as FileEditor
        val composite = getComposite(editor)
        if (composite != null) {
          updateFileIcon(composite.file)
        }
      }
      else if (FileEditor.PROP_VALID == propertyName) {
        val valid = e.newValue as Boolean
        if (!valid) {
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

  private inner class MyRootListener : ModuleRootListener, AdditionalLibraryRootsListener {
    private val EDITOR_FILE_SWAPPER_EP_NAME = ExtensionPointName<EditorFileSwapper>("com.intellij.editorFileSwapper")

    @OptIn(FlowPreview::class)
    private val rootChangedRequests by lazy {
      val flow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      coroutineScope.launch {
        flow
          .debounce(100.milliseconds)
          .collectLatest {
            val allEditors = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              windows.flatMap(EditorWindow::allComposites)
            }

            val replacements = smartReadAction(project) {
              calcEditorReplacements(allEditors)
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              replaceEditors(replacements)
            }
          }
      }
      flow
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      check(rootChangedRequests.tryEmit(Unit))
    }

    private fun calcEditorReplacements(composites: List<EditorComposite>): Map<EditorComposite, kotlin.Pair<VirtualFile, Int?>> {
      val swappers = EDITOR_FILE_SWAPPER_EP_NAME.extensionList
      return composites.asSequence().mapNotNull { composite ->
        if (composite.file.isValid) {
          for (each in swappers) {
            val fileAndOffset = each.getFileToSwapTo(project, composite)
            if (fileAndOffset != null) {
              return@mapNotNull composite to fileAndOffset
            }
          }
        }
        null
      }.associate { it }
    }

    private fun replaceEditors(replacements: Map<EditorComposite, kotlin.Pair<VirtualFile, Int?>>) {
      if (replacements.isEmpty()) {
        return
      }

      for (eachWindow in windows) {
        val selected = eachWindow.selectedComposite
        val composites = eachWindow.allComposites
        for (i in composites.indices) {
          val composite = composites[i]
          val file = composite.file.takeIf { it.isValid } ?: continue
          val newFilePair = replacements.get(composite) ?: continue
          val newFile = newFilePair.first
          // already open
          if (eachWindow.findFileIndex(newFile) != -1) {
            continue
          }

          val openResult = openFileImpl2(window = eachWindow,
                                         file = newFile,
                                         options = FileEditorOpenOptions(index = i, requestFocus = composite === selected))
          val position = newFilePair.second
          if (position != null) {
            val openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(openResult.allEditors)?.editor
            if (openedEditor != null) {
              openedEditor.caretModel.moveToOffset(position)
              openedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
          }
          closeFile(file, eachWindow)
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
      for (each in getAllSplitters()) {
        each.setTabsPlacement(uiSettings.editorTabPlacement)
        each.trimToSize()

        // Tab layout policy
        if (uiSettings.scrollTabLayoutInEditor) {
          each.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
        }
        else {
          each.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT)
        }
      }

      // "Mark modified files with asterisk"
      for (file in openedFiles.asReversed()) {
        updateFileIcon(file)
        updateFileName(file)
        updateFileBackgroundColor(file)
      }

      // "Show full paths in window header"
      coroutineScope.launch {
        withContext(Dispatchers.EDT) { getActiveSplittersAsync() }.await()?.updateFileName(null)
      }
    }
  }

  /**
   * Listen for preview status change to update file tooltip
   */
  private inner class MyEditorCompositeListener : EditorCompositeListener {
    override fun isPreviewChanged(composite: EditorComposite, value: Boolean) {
      updateFileName(composite.file)
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
    for (window in getAllSplitters().flatMap(EditorsSplitters::getWindowSequence)) {
      for (file in window.getFileSequence().toList()) {
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

  fun getSelectionHistory(): List<Pair<VirtualFile, EditorWindow>> {
    return selectionHistory.getHistory().map { Pair(it.first, it.second) }
  }

  final override fun addSelectionRecord(file: VirtualFile, window: EditorWindow) {
    selectionHistory.addRecord(file, window)
  }

  fun removeSelectionRecord(file: VirtualFile, window: EditorWindow) {
    selectionHistory.removeRecord(file, window)
    updateFileName(file)
  }

  override fun refreshIcons() {
    if (!initJob.isCompleted) {
      return
    }

    val openedFiles = openedFiles
    for (each in getAllSplitters()) {
      for (file in openedFiles) {
        each.updateFileIcon(file)
      }
    }
  }

  internal suspend fun openFileOnStartup(windowDeferred: Deferred<EditorWindow>,
                                         file: VirtualFile,
                                         entry: HistoryEntry,
                                         options: FileEditorOpenOptions,
                                         newProviders: List<FileEditorProvider>) {
    if (!canOpenFile(file = file, providers = newProviders)) {
      return
    }

    // the file is not opened yet - in this case we have to create editors and select the created EditorComposite
    coroutineScope {
      val builders = createBuilders(providers = newProviders, file = file, project = project)
      val window = windowDeferred.await()
      val existingComposite = withContext(Dispatchers.EDT) { window.getComposite(file) }
      // ok, now we can await for builders (probably it is already computed as we waited for a window and composite)
      val providerWithBuilderList = newProviders.mapIndexed { index, provider -> provider to builders.get(index)?.await() }
      openFileInEdt(existingComposite = existingComposite,
                    window = window,
                    file = file,
                    entry = entry,
                    options = options,
                    providerWithBuilderList = providerWithBuilderList)
    }
  }

  private suspend fun openFileInEdt(
    existingComposite: EditorComposite?,
    window: EditorWindow,
    file: VirtualFile,
    entry: HistoryEntry?,
    options: FileEditorOpenOptions,
    providerWithBuilderList: List<kotlin.Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>,
  ): FileEditorComposite {
    val startTime = System.nanoTime()
    val isNewEditor = existingComposite == null
    val (result, fireFileOpened) = coroutineScope {
      val deferredPublishers: Deferred<kotlin.Pair<FileOpenedSyncListener, FileEditorManagerListener>>? = if (isNewEditor) {
        async(CoroutineName("FileOpenedSyncListener computing")) {
          val messageBus = project.messageBus
          messageBus.syncAndPreloadPublisher(FileOpenedSyncListener.TOPIC) to
            messageBus.syncAndPreloadPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
        }
      }
      else {
        null
      }

      val beforePublisher = if (isNewEditor) {
        project.messageBus.syncAndPreloadPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)
      }
      else {
        null
      }

      subtask("file opening in EDT", Dispatchers.EDT) {
        val splitters = window.owner
        runBulkTabChangeInEdt(splitters) {
          var composite: EditorComposite? = existingComposite

          if (isNewEditor) {
            composite = createComposite(file = file, providers = providerWithBuilderList)
            if (composite != null) {
              runActivity("beforeFileOpened event executing") {
                beforePublisher!!.beforeFileOpened(this@FileEditorManagerImpl, file)
              }
              openedComposites.add(composite)
            }
          }

          val result = if (composite == null) {
            FileEditorComposite.EMPTY
          }
          else {
            openInEdtImpl(composite = composite,
                          window = window,
                          file = file,
                          entry = entry,
                          options = options,
                          newEditor = isNewEditor)
          }
          if (isNewEditor && result is EditorComposite) {
            val editorsWithProviders = result.allEditorsWithProviders
            val (goodPublisher, deprecatedPublisher) = deferredPublishers!!.await()
            goodPublisher.fileOpenedSync(this@FileEditorManagerImpl, file, editorsWithProviders)
            @Suppress("DEPRECATION")
            deprecatedPublisher.fileOpenedSync(this@FileEditorManagerImpl, file, editorsWithProviders)
            return@subtask result to true
          }
          result to false
        }
      }
    }

    if (fireFileOpened) {
      triggerOpen(project = project, file = file, start = startTime, composite = result)

      val publisher = project.messageBus.syncAndPreloadPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
      // must be executed with a current modality — that's why coroutineScope.launch should be not used
      subtask("fileOpened event executing", Dispatchers.EDT) {
        if (isFileOpen(file)) {
          runCatching {
            publisher.fileOpened(this@FileEditorManagerImpl, file)
          }.getOrLogException(LOG)
        }
      }
    }
    return result
  }

  @Suppress("DuplicatedCode")
  private fun openInEdtImpl(
    composite: EditorComposite,
    window: EditorWindow,
    file: VirtualFile,
    entry: HistoryEntry?,
    options: FileEditorOpenOptions,
    newEditor: Boolean,
  ): FileEditorComposite {
    val editorsWithProviders = composite.allEditorsWithProviders
    window.setComposite(composite, options)
    for (editorWithProvider in editorsWithProviders) {
      restoreEditorState(file = file,
                         editorWithProvider = editorWithProvider,
                         entry = entry,
                         newEditor = newEditor,
                         exactState = options.isExactState)
    }

    // restore selected editor
    val provider = if (entry == null) {
      FileEditorProviderManagerImpl.getInstanceImpl().getSelectedFileEditorProvider(composite, project)
    }
    else {
      entry.selectedProvider
    }
    if (provider != null) {
      composite.setSelectedEditor(provider.editorTypeId)
    }

    // notify editors about selection changes
    val splitters = window.owner
    splitters.setCurrentWindow(window = window, requestFocus = false)

    splitters.afterFileOpen(file)
    addSelectionRecord(file, window)
    val selectedEditor = composite.selectedEditor
    selectedEditor?.selectNotify()

    if (newEditor) {
      openFileSetModificationCount.increment()
    }

    // update frame and tab title
    updateFileName(file)

    if (options.pin) {
      window.setFilePinned(composite, pinned = true)
    }

    // transfer focus into editor
    if (options.requestFocus && !ApplicationManager.getApplication().isUnitTestMode) {
      if (selectedEditor is TextEditor) {
        runWhenLoaded(selectedEditor.editor) {
          // while the editor was loading asynchronously, the user switched to another editor - don't steal focus
          if (splitters.currentWindow === window && window.selectedComposite === composite) {
            composite.preferredFocusedComponent?.requestFocusInWindow()
            IdeFocusManager.getGlobalInstance().toFront(splitters)
          }
        }

      }
      else {
        composite.preferredFocusedComponent?.requestFocusInWindow()
        IdeFocusManager.getGlobalInstance().toFront(splitters)
      }
    }
    return composite
  }

  @Internal
  open fun forceUseUiInHeadlessMode(): Boolean = false

  @TestOnly
  fun waitForAsyncUpdateOnDumbModeFinished() {
    runBlockingMaybeCancellable {
      val job = dumbModeFinishedScope.coroutineContext.job
      while (true) {
        UIUtil.dispatchAllInvocationEvents()
        yield()

        val jobs = job.children.toList()
        if (jobs.isEmpty()) {
          break
        }

        UIUtil.dispatchAllInvocationEvents()
        yield()
      }
    }
  }

  private fun triggerOpen(project: Project, file: VirtualFile, start: Long, composite: FileEditorComposite) {
    StartUpMeasurer.addCompletedActivity(start, "editor time-to-show", ActivityCategory.DEFAULT, null)

    val timeToShow = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    val fileEditor = composite.allEditors.firstOrNull()
    val editor = if (fileEditor is TextEditor) fileEditor.getEditor() else null
    if (editor == null) {
      coroutineScope.launch {
        FileTypeUsageCounterCollector.logOpened(project, file, fileEditor, timeToShow, -1, composite)
      }
    }
    else {
      AsyncEditorLoader.performWhenLoaded(editor) {
        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        StartUpMeasurer.addCompletedActivity(start, "editor time-to-edit", ActivityCategory.DEFAULT, null)
        coroutineScope.launch {
          FileTypeUsageCounterCollector.logOpened(project, file, fileEditor, timeToShow, durationMs, composite)
        }
      }
    }
  }
}

@Deprecated("Please use EditorComposite directly")
open class EditorWithProviderComposite @Internal constructor(
  file: VirtualFile,
  editorsWithProviders: List<FileEditorWithProvider>,
  project: Project,
) : EditorComposite(file = file, editorsWithProviders = editorsWithProviders, project = project)

private class SelectionHistory {
  private val history = ArrayList<kotlin.Pair<VirtualFile, EditorWindow>>()

  @Synchronized
  fun addRecord(file: VirtualFile, window: EditorWindow) {
    val record = file to window
    history.remove(record)
    history.add(0, record)
  }

  @Synchronized
  fun removeRecord(file: VirtualFile, window: EditorWindow) {
    history.remove(file to window)
  }

  @Synchronized
  fun getHistory(): Collection<kotlin.Pair<VirtualFile, EditorWindow>> {
    val copy = LinkedHashSet<kotlin.Pair<VirtualFile, EditorWindow>>()
    for (pair in history) {
      if (pair.second.getFileSequence().none()) {
        val windows = pair.second.owner.getWindows()
        if (windows.isNotEmpty() && windows[0].getFileSequence().any()) {
          copy.add(pair.first to windows[0])
        }
      }
      else {
        copy.add(pair)
      }
    }
    history.clear()
    history.addAll(copy)
    return copy
  }
}

private class SelectionState(@JvmField val composite: EditorComposite, @JvmField val fileEditorProvider: FileEditorWithProvider)

@Internal
suspend fun FileEditorComposite.waitForFullyLoaded() {
  for (editor in allEditors) {
    if (editor is TextEditor) {
      AsyncEditorLoader.waitForLoaded(editor.editor)
    }
  }
}

private fun getEffectiveOptions(options: FileEditorOpenOptions, entry: HistoryEntry?): FileEditorOpenOptions {
  if (entry != null && entry.isPreview) {
    return options.copy(usePreviewTab = false)
  }
  return options
}

private fun CoroutineScope.createBuilders(providers: List<FileEditorProvider>,
                                          file: VirtualFile,
                                          project: Project): List<Deferred<AsyncFileEditorProvider.Builder?>?> {
  return providers.map { provider ->
    if (provider !is AsyncFileEditorProvider) {
      return@map null
    }
    async {
      runCatching {
        provider.createEditorBuilder(project = project, file = file)
      }.getOrLogException(LOG)
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
  if (!EDT.isCurrentThreadEdt()) {
    return task()
  }

  return runBulkTabChangeInEdt(splitters, task)
}

@RequiresEdt
internal inline fun <T> runBulkTabChangeInEdt(splitters: EditorsSplitters, task: () -> T): T {
  splitters.insideChange++
  try {
    return task()
  }
  finally {
    splitters.insideChange--
    if (!splitters.isInsideChange) {
      splitters.validate()
      for (window in splitters.getWindows()) {
        (window.tabbedPane.tabs as JBTabsImpl).revalidateAndRepaint()
      }
    }
  }
}