// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION", "ReplaceGetOrSet", "LeakingThis")
@file:OptIn(FlowPreview::class, FlowPreview::class, FlowPreview::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.ProjectTopics
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.runActivity
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.SplitAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.LangBundle
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager.Companion.getProjectSession
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.FileEditorComposite.Companion.EMPTY
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorComposite.Companion.retrofit
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl.Companion.getInstanceImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.*
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
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.flow.zipWithNext
import com.intellij.util.messages.impl.MessageListenerList
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.Runnable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@State(name = "FileEditorManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
open class FileEditorManagerImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : FileEditorManagerEx(), PersistentStateComponent<Element?>, Disposable {
  enum class OpenMode {
    NEW_WINDOW, RIGHT_SPLIT, DEFAULT
  }

  // temporarily used during initialization
  private val state = AtomicReference<EditorSplitterState?>()

  lateinit var mainSplitters: EditorsSplitters
    private set

  private val isInitialized = AtomicBoolean()

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

  override val currentFileEditorFlow: StateFlow<FileEditor?>

  override val dockContainer: DockContainer?
    get() = dockable.value

  init {
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

    // not using collectLatest() to ensure that no selection update is missed by the listeners
    selectionFlow
      .zipWithNext { oldState, state ->
        val oldEditorWithProvider = oldState?.fileEditorProvider
        val newEditorWithProvider = state?.fileEditorProvider
        if (oldEditorWithProvider == newEditorWithProvider) {
          return@zipWithNext
        }

        // expected in EDT
        withContext(Dispatchers.EDT) {
          kotlin.runCatching {
            fireSelectionChanged(oldComposite = oldState?.composite,
                                 newComposite = state?.composite,
                                 oldEditorWithProvider = oldEditorWithProvider,
                                 newEditorWithProvider = newEditorWithProvider,
                                 publisher = publisher)
          }.getOrLogException(LOG)
        }
      }
      .launchIn(coroutineScope)

    currentFileEditorFlow = selectionFlow
      .map { it?.fileEditorProvider?.fileEditor }
      .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    project.messageBus.connect(coroutineScope).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        coroutineScope.launch {
          dumbModeFinished(project)
        }
      }
    })
    closeFilesOnFileEditorRemoval()

    if (ApplicationManager.getApplication().isUnitTestMode || forceUseUiInHeadlessMode()) {
      isInitialized.set(true)
      mainSplitters = EditorsSplitters(manager = this, coroutineScope = coroutineScope)
      check(splitterFlow.tryEmit(mainSplitters))
    }
  }

  @RequiresEdt
  internal fun init(): kotlin.Pair<EditorsSplitters, EditorSplitterState?> {
    if (isInitialized.get()) {
      LOG.error("already initialized")
    }

    val component = EditorsSplitters(manager = this, coroutineScope = coroutineScope)
    component.isFocusable = false
    // prepare for toolwindow manager
    mainSplitters = component
    check(splitterFlow.tryEmit(component))

    // set after we set mainSplitters
    if (!isInitialized.compareAndSet(false, true)) {
      LOG.error("already initialized")
    }

    // connect after we set mainSplitters
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      val connection = project.messageBus.connect(coroutineScope)
      connection.subscribe(FileStatusListener.TOPIC, MyFileStatusListener())
      connection.subscribe(FileTypeManager.TOPIC, MyFileTypeListener())
      if (!LightEdit.owns(project)) {
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, MyRootListener())
        connection.subscribe(AdditionalLibraryRootsListener.TOPIC, MyRootListener())
      }

      // updates tabs names
      connection.subscribe(VirtualFileManager.VFS_CHANGES, MyVirtualFileListener())

      // extends/cuts number of opened tabs. Also updates location of tabs
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

    return component to state.getAndSet(null)
  }

  companion object {
    private val LOG = logger<FileEditorManagerImpl>()

    @JvmField
    protected val DUMB_AWARE = Key.create<Boolean>("DUMB_AWARE")

    @JvmField
    val NOTHING_WAS_OPENED_ON_START = Key.create<Boolean>("NOTHING_WAS_OPENED_ON_START")

    @JvmField
    val CLOSING_TO_REOPEN = Key.create<Boolean>("CLOSING_TO_REOPEN")

    /**
     * Works on VirtualFile objects and allows disabling the Preview Tab functionality for certain files.
     * If a virtual file has, this key is set to TRUE, the corresponding editor will always be opened in a regular tab.
     */
    @JvmField
    val FORBID_PREVIEW_TAB = Key.create<Boolean>("FORBID_PREVIEW_TAB")

    @JvmField
    val OPEN_IN_PREVIEW_TAB = Key.create<Boolean>("OPEN_IN_PREVIEW_TAB")

    /**
     * Works on [FileEditor] objects, allows forcing opening other editor tabs in the main window.
     * When determining a proper place to open a new editor tab, the currently selected file editor is checked
     * whether is has this key set to TRUE. If that's the case, and the selected editor is a singleton in a split view,
     * the new editor tab is opened in the sibling of that split window. If the singleton editor is not in a split view,
     * but in a separate detached window, then the new editors will be opened in the main window splitters.
     */
    @JvmField
    val SINGLETON_EDITOR_IN_WINDOW = Key.create<Boolean>("OPEN_OTHER_TABS_IN_MAIN_WINDOW")

    const val FILE_EDITOR_MANAGER = "FileEditorManager"
    const val EDITOR_OPEN_INACTIVE_SPLITTER = "editor.open.inactive.splitter"
    private val openFileSetModificationCount = LongAdder()

    @JvmField
    val OPEN_FILE_SET_MODIFICATION_COUNT = ModificationTracker { openFileSetModificationCount.sum() }

    fun isDumbAware(editor: FileEditor): Boolean {
      return true == editor.getUserData(DUMB_AWARE) && (editor !is PossiblyDumbAware || (editor as PossiblyDumbAware).isDumbAware)
    }

    private fun isFileOpenInWindow(file: VirtualFile, window: EditorWindow): Boolean {
      val shouldFileBeSelected = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE
      return if (shouldFileBeSelected) file == window.selectedFile else window.isFileOpen(file)
    }

    @JvmStatic
    fun getOpenMode(event: AWTEvent): OpenMode {
      if (event is MouseEvent) {
        val isMouseClick = event.getID() == MouseEvent.MOUSE_CLICKED || event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED
        val modifiers = event.modifiersEx
        if (modifiers == InputEvent.SHIFT_DOWN_MASK && isMouseClick) {
          return OpenMode.NEW_WINDOW
        }
      }
      else if (event is KeyEvent) {
        val keymapManager = KeymapManager.getInstance()
        if (keymapManager != null) {
          @Suppress("DEPRECATION")
          val strings = keymapManager.activeKeymap.getActionIds(KeyStroke.getKeyStroke(event.keyCode, event.modifiers))
          if (strings.contains(IdeActions.ACTION_OPEN_IN_NEW_WINDOW)) {
            return OpenMode.NEW_WINDOW
          }
          if (strings.contains(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)) {
            return OpenMode.RIGHT_SPLIT
          }
        }
      }
      return OpenMode.DEFAULT
    }

    @JvmStatic
    fun forbidSplitFor(file: VirtualFile): Boolean = file.getUserData(SplitAction.FORBID_TAB_SPLIT) == true

    @JvmStatic
    internal fun isSingletonFileEditor(fileEditor: FileEditor?): Boolean = SINGLETON_EDITOR_IN_WINDOW.get(fileEditor, false)

    internal fun getOriginalFile(file: VirtualFile): VirtualFile {
      return BackedVirtualFile.getOriginFileIfBacked(if (file is VirtualFileWindow) file.delegate else file)
    }

    internal fun <T> runBulkTabChange(splitters: EditorsSplitters, task: () -> T): T {
      if (!ApplicationManager.getApplication().isDispatchThread) {
        return task()
      }
      else {
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
    if (!isInitialized.get()) {
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
      if (!isInitialized.get()) {
        return null
      }
      return getActiveSplitterSync().currentFile
    }

  override val activeWindow: CompletableFuture<EditorWindow?>
    get() = getActiveSplittersAsync().asCompletableFuture().thenApply { it?.currentWindow }

  override var currentWindow: EditorWindow?
    get() {
      if (!ClientId.isCurrentlyUnderLocalId || !isInitialized.get()) {
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

  @RequiresEdt
  internal fun closeFile(window: EditorWindow, composite: EditorComposite) {
    openFileSetModificationCount.increment()
    val file = composite.file
    CommandProcessor.getInstance().executeCommand(project, {
      window.closeFile(file = file, composite = composite)
    }, IdeBundle.message("command.close.active.editor"), null)
    removeSelectionRecord(file, window)
  }

  @RequiresEdt
  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    closeFile(window = window, composite = window.getComposite(file) ?: return)
  }

  override fun closeFile(file: VirtualFile) {
    closeFile(file = file, moveFocus = true, closeAllCopies = false)
  }

  @RequiresEdt
  fun closeFile(file: VirtualFile, moveFocus: Boolean, closeAllCopies: Boolean) {
    if (!closeAllCopies) {
      if (ClientId.isCurrentlyUnderLocalId) {
        CommandProcessor.getInstance().executeCommand(project, {
          openFileSetModificationCount.increment()
          val activeSplitters = getActiveSplitterSync()
          runBulkTabChange(activeSplitters) { activeSplitters.closeFile(file, moveFocus) }
        }, "", null)
      }
      else {
        clientFileEditorManager?.closeFile(file, false)
      }
    }
    else {
      withClientId(ClientId.localId).use {
        CommandProcessor.getInstance().executeCommand(project, {
          openFileSetModificationCount.increment()
          for (each in getAllSplitters()) {
            runBulkTabChange(each) { each.closeFile(file = file, moveFocus = moveFocus) }
          }
        }, "", null)
      }
      for (manager in allClientFileEditorManagers) {
        manager.closeFile(file = file, closeAllCopies = true)
      }
    }
  }

  private fun closeFileEditor(editor: FileEditor, moveFocus: Boolean = true) {
    val file = editor.file ?: return
    if (ClientId.isCurrentlyUnderLocalId) {
      CommandProcessor.getInstance().executeCommand(project, {
        openFileSetModificationCount.increment()
        for (each in getAllSplitters()) {
          runBulkTabChange(each) { each.closeFileEditor(file, editor, moveFocus) }
        }
      }, "", null)
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
      if (mode == OpenMode.RIGHT_SPLIT) {
        val result = openInRightSplit(file)
        if (result != null) {
          return result
        }
      }
    }

    if (windowToOpenIn == null && (options.reuseOpen || !AdvancedSettings.getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER))) {
      windowToOpenIn = findWindowInAllSplitters(file)
    }
    if (windowToOpenIn == null) {
      windowToOpenIn = getOrCreateCurrentWindow(file)
    }
    return openFileImpl2(window = windowToOpenIn, file = file, options = options)
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
    var result: FileEditorComposite? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val editorsWithProviders = split.getComposites().flatMap(EditorComposite::allEditorsWithProviders).toList()
      val allEditors = editorsWithProviders.map { it.fileEditor }
      val allProviders = editorsWithProviders.map { it.provider }
      result = object : FileEditorComposite {
        override val isPreview: Boolean
          get() = false

        override val allEditors: List<FileEditor>
          get() = allEditors
        override val allProviders: List<FileEditorProvider>
          get() = allProviders
      }
    }, "", null)
    return result
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

    var result: FileEditorComposite? = null
    CommandProcessor.getInstance().executeCommand(project, {
      result = openFileImpl4(window = window, _file = file, entry = null, options = options)
    }, "", null)
    return result!!
  }

  /**
   * Unlike the openFile method, file can be invalid.
   * For example, all files were invalidated, and they are being removed one by one.
   * If we have removed one invalid file, then another invalid file becomes selected.
   * That's why we do not require that passed file is valid.
   */
  internal fun openMaybeInvalidFile(window: EditorWindow, file: VirtualFile, entry: HistoryEntry?): FileEditorComposite {
    return openFileImpl4(window = window, _file = file, entry = entry, options = FileEditorOpenOptions(requestFocus = true))
  }

  private val clientFileEditorManager: ClientFileEditorManager?
    get() {
      val clientId = ClientId.current
      LOG.assertTrue(!clientId.isLocal, "Trying to get ClientFileEditorManager for local ClientId")
      return getProjectSession(project, clientId)?.serviceOrNull<ClientFileEditorManager>()
    }

  /**
   * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
   */
  internal fun openFileImpl4(window: EditorWindow,
                             @Suppress("LocalVariableName") _file: VirtualFile,
                             entry: HistoryEntry?,
                             options: FileEditorOpenOptions): FileEditorComposite {
    assert(ApplicationManager.getApplication().isDispatchThread ||
           !ApplicationManager.getApplication().isReadAccessAllowed) { "must not attempt opening files under read action" }
    if (!ClientId.isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return EMPTY
      val result = clientManager.openFile(file = _file, forceCreate = false)
      val allEditors = result.map { it.fileEditor }
      val allProviders = result.map { it.provider }
      return object : FileEditorComposite {
        override val isPreview: Boolean
          get() = options.usePreviewTab

        override val allEditors: List<FileEditor>
          get() = allEditors
        override val allProviders: List<FileEditorProvider>
          get() = allProviders
      }
    }

    val file = getOriginalFile(_file)
    val existingComposite: FileEditorComposite? = if (EDT.isCurrentThreadEdt()) {
      window.getComposite(file)
    }
    else {
      runBlockingCancellable { withContext(Dispatchers.EDT) { window.getComposite(file) } }
    }

    val newProviders: List<FileEditorProvider>?
    val builders: Array<AsyncFileEditorProvider.Builder?>?
    if (existingComposite == null) {
      if (!canOpenFile(file)) {
        return EMPTY
      }

      // File is not opened yet. In this case, we have to create editors and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviderList(project, file)
      builders = arrayOfNulls(newProviders.size)
      for (i in newProviders.indices) {
        try {
          val provider = newProviders[i]
          builders[i] = ApplicationManager.getApplication().runReadAction<AsyncFileEditorProvider.Builder?, RuntimeException> {
            if (project.isDisposed || !file.isValid) {
              return@runReadAction null
            }
            LOG.assertTrue(provider.accept(project, file), "Provider $provider doesn't accept file $file")
            if (provider is AsyncFileEditorProvider) provider.createEditorAsync(project, file) else null
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
    else {
      newProviders = null
      builders = null
    }

    fun open(): FileEditorComposite {
      return runBulkTabChange(window.owner) {
        var effectiveOptions = options
        (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
        LOG.assertTrue(file.isValid, "Invalid file: $file")
        if (effectiveOptions.requestFocus) {
          val activeProject = ProjectUtil.getActiveProject()
          if (activeProject != null && activeProject != project) {
            // allow focus switching only within a project
            effectiveOptions = effectiveOptions.copy(requestFocus = false)
          }
        }
        if (entry != null && entry.isPreview) {
          effectiveOptions = effectiveOptions.copy(usePreviewTab = false)
        }
        val (result, opened) = doOpenInEdtImpl(window = window,
                                               file = file,
                                               entry = entry,
                                               options = effectiveOptions,
                                               newProviders = newProviders,
                                               builders = builders?.asList() ?: emptyList())
        if (opened != null) {
          ApplicationManager.getApplication().invokeLater(Runnable { opened() }, project.disposed)
        }
        result
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

  private fun doOpenInEdtImpl(
    window: EditorWindow,
    file: VirtualFile,
    entry: HistoryEntry?,
    options: FileEditorOpenOptions,
    newProviders: List<FileEditorProvider>?,
    builders: List<AsyncFileEditorProvider.Builder?>,
    isReopeningOnStartup: Boolean = false,
  ): kotlin.Pair<FileEditorComposite, (() -> Unit)?> {
    var composite = window.getComposite(file)
    val newEditor = composite == null
    if (newEditor) {
      LOG.assertTrue(newProviders != null)
      composite = createComposite(file = file, providers = newProviders!!, builders = builders) ?: return EMPTY to null
      runActivity("beforeFileOpened event executing") {
        project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER).beforeFileOpened(this, file)
      }
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
    val provider = if (entry == null) getInstanceImpl().getSelectedFileEditorProvider(composite, project) else entry.selectedProvider
    if (provider != null) {
      composite.setSelectedEditor(provider.editorTypeId)
    }

    // notify editors about selection changes
    val splitters = window.owner
    splitters.setCurrentWindow(window, options.requestFocus)
    splitters.afterFileOpen(file)
    addSelectionRecord(file, window)
    val selectedEditor = composite.selectedEditor
    selectedEditor?.selectNotify()

    // transfer focus into editor
    if (options.requestFocus && !ApplicationManager.getApplication().isUnitTestMode) {
      val focusRunnable = Runnable {
        if (splitters.currentWindow != window || window.selectedComposite !== composite) {
          // While the editor was loading asynchronously, the user switched to another editor.
          // Don't steal focus.
          return@Runnable
        }
        val windowAncestor = SwingUtilities.getWindowAncestor(window.panel)
        if (windowAncestor != null && windowAncestor == KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow) {
          composite.preferredFocusedComponent?.requestFocus()
        }
      }
      if (selectedEditor is TextEditor) {
        runWhenLoaded(selectedEditor.editor, focusRunnable)
      }
      else {
        focusRunnable.run()
      }
      IdeFocusManager.getInstance(project).toFront(splitters)
    }
    if (newEditor) {
      openFileSetModificationCount.increment()
    }

    if (!isReopeningOnStartup) {
      //[jeka] this is a hack to support back-forward navigation
      // previously there was an incorrect call to fireSelectionChanged() with a side-effect
      IdeDocumentHistory.getInstance(project).onSelectionChanged()
    }

    // update frame and tab title
    updateFileName(file)

    if (options.pin) {
      window.setFilePinned(composite, pinned = true)
    }

    if (newEditor) {
      val messageBus = project.messageBus
      messageBus.syncPublisher(FileOpenedSyncListener.TOPIC).fileOpenedSync(this, file, editorsWithProviders)
      @Suppress("DEPRECATION")
      messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpenedSync(this, file, editorsWithProviders)
      return composite to {
        if (isFileOpen(file)) {
          project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(this@FileEditorManagerImpl, file)
        }
      }
    }
    return composite to null
  }

  protected open fun createComposite(file: VirtualFile,
                                     providers: List<FileEditorProvider>,
                                     builders: List<AsyncFileEditorProvider.Builder?>): EditorComposite? {
    val editorsWithProviders = ArrayList<FileEditorWithProvider>(providers.size)
    for (i in providers.indices) {
      try {
        val provider = providers[i]
        val builder = if (builders.isEmpty()) null else builders[i]
        val editor = if (builder == null) provider.createEditor(project, file) else builder.build()
        if (!editor.isValid) {
          val pluginDescriptor = PluginManager.getPluginByClass(provider.javaClass)
          LOG.error(PluginException("Invalid editor created by provider ${provider.javaClass.name}", pluginDescriptor?.pluginId))
          continue
        }
        editorsWithProviders.add(FileEditorWithProvider(editor, provider))
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
    if (editorsWithProviders.isEmpty()) return null
    return createComposite(file, editorsWithProviders)
  }

  protected fun createComposite(file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      editor.addPropertyChangeListener(editorPropertyChangeListener)
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
    }
    return createCompositeInstance(file, editorsWithProviders)?.also { composite ->
      composite.addListener(editorCompositeListener, disposable = this)
    }
  }

  @Contract("_, _ -> new")
  protected fun createCompositeInstance(file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.createComposite(file, editorsWithProviders)
    }
    // the only place this class is created, won't be needed when we get rid of EditorWithProviderComposite usages
    @Suppress("DEPRECATION")
    return EditorWithProviderComposite(file = file, editorsWithProviders = editorsWithProviders, project = project)
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
      // We have to try to get state from the history only in case f the editor is not opened.
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
    val newComposite = createComposite(file = file, providers = providers, builders = emptyList()) ?: return null
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
  private fun openEditorImpl(descriptor: FileEditorNavigatable, focusEditor: Boolean): Pair<List<FileEditor>, FileEditor> {
    val realDescriptor: FileEditorNavigatable
    if (descriptor is OpenFileDescriptor && descriptor.getFile() is VirtualFileWindow) {
      val delegate = descriptor.getFile() as VirtualFileWindow
      val hostOffset = delegate.documentWindow.injectedToHost(descriptor.offset)
      val fixedDescriptor = OpenFileDescriptor(descriptor.project, delegate.delegate, hostOffset)
      fixedDescriptor.isUseCurrentWindow = descriptor.isUseCurrentWindow()
      fixedDescriptor.isUsePreviewTab = descriptor.isUsePreviewTab()
      realDescriptor = fixedDescriptor
    }
    else {
      realDescriptor = descriptor
    }

    var result: List<FileEditor> = emptyList()
    var selectedEditor: FileEditor? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val file = realDescriptor.file
      val openOptions = FileEditorOpenOptions(
        reuseOpen = !realDescriptor.isUseCurrentWindow,
        usePreviewTab = realDescriptor.isUsePreviewTab,
        requestFocus = focusEditor,
      )
      val editors = openFile(file = file, window = null, options = openOptions).allEditors
      result = editors
      var navigated = false
      for (editor in editors) {
        if (editor is NavigatableFileEditor && getSelectedEditor(realDescriptor.file) === editor) { // try to navigate opened editor
          navigated = navigateAndSelectEditor(editor, realDescriptor)
          if (navigated) {
            selectedEditor = editor
            break
          }
        }
      }
      if (!navigated) {
        for (editor in editors) {
          if (editor is NavigatableFileEditor && getSelectedEditor(realDescriptor.file) !== editor) { // try other editors
            if (navigateAndSelectEditor(editor, realDescriptor)) {
              selectedEditor = editor
              break
            }
          }
        }
      }
    }, "", null)
    return Pair(result, selectedEditor)
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

    var target = if (selectedEditor is TextEditor) selectedEditor else textEditors[0]
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

  override fun getSelectedEditorWithRemotes(): Array<FileEditor> {
    val result = ArrayList<FileEditor>()
    result.addAll(selectedEditors)
    for (m in allClientFileEditorManagers) {
      result.addAll(m.getSelectedEditors())
    }
    return result.toTypedArray()
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
    if (!isInitialized.get()) {
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
    if (!isInitialized.get()) {
      return FileEditor.EMPTY_ARRAY
    }

    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditors()?.toTypedArray() ?: FileEditor.EMPTY_ARRAY
    }
    val selectedEditors = SmartHashSet<FileEditor>()
    for (splitters in getAllSplitters()) {
      splitters.addSelectedEditorsTo(selectedEditors)
    }
    return selectedEditors.toTypedArray()
  }

  override val splitters: EditorsSplitters
    get() = if (ApplicationManager.getApplication().isDispatchThread) getActiveSplitterSync() else mainSplitters

  @get:RequiresEdt
  override val activeSplittersComposites: List<EditorComposite>
    get() = if (isInitialized.get()) getActiveSplitterSync().getAllComposites() else emptyList()

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
    if (!isInitialized.get()) {
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
  override fun getEditors(file: VirtualFile): Array<FileEditor> = getComposite(file)?.allEditors?.toTypedArray() ?: FileEditor.EMPTY_ARRAY

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
    if (!ClientId.isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getComposite(file)
    }

    if (openedComposites.isEmpty()) {
      return null
    }

    val originalFile = getOriginalFile(file)
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
    if (!isInitialized.get()) {
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
    if (!isInitialized.get()) {
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

  private fun fireSelectionChanged(oldComposite: EditorComposite?,
                                   newComposite: EditorComposite?,
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

    if (oldComposite?.file == newComposite?.file) {
      CommandProcessor.getInstance().executeCommand(project, task, IdeBundle.message("command.switch.active.editor"), null)
    }
    else {
      task()
    }

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

  override val isInsideChange: Boolean
    get() = splitters.isInsideChange

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
        ApplicationManager.getApplication().invokeLater({
                                                          if (LOG.isDebugEnabled) {
                                                            LOG.debug("updating file status in tab for " + file.path)
                                                          }
                                                          updateFileStatus(file)
                                                        }, ModalityState.NON_MODAL, project.disposed)
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
    CommandProcessor.getInstance().executeCommand(project, {
      openFileSetModificationCount.increment()
      val splitters = getActiveSplitterSync()
      if (repaint) {
        runBulkTabChange(splitters, splitters::closeAllFiles)
      }
      else {
        splitters.closeAllFiles(repaint = false)
      }
    }, "", null)
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
    if (!canOpenFile(file, newProviders)) {
      return
    }

    // the file is not opened yet - in this case we have to create editors and select the created EditorComposite
    val builders = ArrayList<AsyncFileEditorProvider.Builder?>(newProviders.size)
    for (provider in newProviders) {
      val builder = try {
        if (provider is AsyncFileEditorProvider) provider.createEditorAsync(project, file) else null
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        null
      }
      builders.add(builder)
    }

    val window = windowDeferred.await()
    val opened: (() -> Unit)? = withContext(Dispatchers.EDT) {
      runActivity("file opening in EDT") {
        if (!file.isValid) {
          return@withContext null
        }

        val splitters = window.owner
        splitters.insideChange++
        val (_, opened) = try {
          doOpenInEdtImpl(window = window,
                          file = file,
                          entry = entry,
                          options = options,
                          newProviders = newProviders,
                          builders = builders,
                          isReopeningOnStartup = true)
        }
        finally {
          splitters.insideChange--
        }
        opened
      }
    }

    if (opened != null) {
      // must be executed with a current modality — that's why doOpenInEdtImpl cannot use coroutineScope.launch
      withContext(Dispatchers.EDT) {
        opened()
      }
    }
  }

  @ApiStatus.Internal
  open fun forceUseUiInHeadlessMode() = false
}

@Deprecated("Please use EditorComposite directly")
open class EditorWithProviderComposite @ApiStatus.Internal constructor(
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