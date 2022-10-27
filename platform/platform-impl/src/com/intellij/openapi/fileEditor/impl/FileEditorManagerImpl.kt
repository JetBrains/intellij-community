// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION", "ReplaceGetOrSet", "LeakingThis")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ProjectTopics
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.isCurrentlyUnderLocalId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.ClientId.Companion.localId
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.MaximizeEditorInSplitAction.Companion.getSplittersToMaximize
import com.intellij.ide.actions.SplitAction
import com.intellij.ide.impl.ProjectUtil.getActiveProject
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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
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
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.ui.Splitter
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
import com.intellij.reference.SoftReference
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.impl.DockManagerImpl
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.messages.impl.MessageListenerList
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

@State(name = "FileEditorManager", storages = [
  Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
])
open class FileEditorManagerImpl(private val project: Project) : FileEditorManagerEx(), PersistentStateComponent<Element?>, Disposable {
  enum class OpenMode {
    NEW_WINDOW, RIGHT_SPLIT, DEFAULT
  }

  val mainSplitters: EditorsSplitters

  private val dockable: DockableEditorTabbedContainer
  private val selectionHistory = ArrayList<Pair<VirtualFile, EditorWindow>>()
  private var lastSelectedComposite: Reference<EditorComposite?>? = WeakReference<EditorComposite?>(null)
  private val queue = MergingUpdateQueue("FileEditorManagerUpdateQueue", 50, true, MergingUpdateQueue.ANY_COMPONENT, this)
  private var fileToUpdateTitle: SoftReference<VirtualFile?>? = null

  private val updateFileTitleAlarm = SingleAlarm(Runnable {
    val file = SoftReference.deref(fileToUpdateTitle)
    if (file == null || !file.isValid) {
      return@Runnable
    }
    fileToUpdateTitle = null
    for (each in getAllSplitters()) {
      each.updateFileName(file)
    }
  }, 50, this, Alarm.ThreadToUse.SWING_THREAD, ModalityState.NON_MODAL)

  private val busyObject = SimpleBusyObject()

  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  private val editorPropertyChangeListener = MyEditorPropertyChangeListener()
  private var contentFactory: DockableEditorContainerFactory? = null
  private val openedComposites = CopyOnWriteArrayList<EditorComposite>()
  private val listenerList = MessageListenerList(project.messageBus, FileEditorManagerListener.FILE_EDITOR_MANAGER)

  init {
    queue.setTrackUiActivity(true)
    val connection = project.messageBus.connect(this)
    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
        ApplicationManager.getApplication().invokeLater({ dumbModeFinished(project) }, project.disposed)
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (this@FileEditorManagerImpl.project === project) {
          // Dispose created editors. We do not use closeEditor method because
          // it fires event and changes history.
          closeAllFiles()
        }
      }
    })
    closeFilesOnFileEditorRemoval()
    mainSplitters = EditorsSplitters(this)
    dockable = DockableEditorTabbedContainer(mainSplitters, false)
    // prepare for toolwindow manager
    mainSplitters.isFocusable = false
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
     * Works on VirtualFile objects, and allows to disable the Preview Tab functionality for certain files.
     * If a virtual file has this key set to TRUE, the corresponding editor will always be opened in a regular tab.
     */
    @JvmField
    val FORBID_PREVIEW_TAB = Key.create<Boolean>("FORBID_PREVIEW_TAB")
    @JvmField
    val OPEN_IN_PREVIEW_TAB = Key.create<Boolean>("OPEN_IN_PREVIEW_TAB")

    /**
     * Works on FileEditor objects, allows to force opening other editor tabs in the main window.
     * If the currently selected file editor has this key set to TRUE, new editors will be opened in the main splitters.
     */
    val SINGLETON_EDITOR_IN_WINDOW = Key.create<Boolean>("OPEN_OTHER_TABS_IN_MAIN_WINDOW")
    const val FILE_EDITOR_MANAGER = "FileEditorManager"
    const val EDITOR_OPEN_INACTIVE_SPLITTER = "editor.open.inactive.splitter"
    private val openFileSetModificationCount = AtomicInteger()
    @JvmField
    val OPEN_FILE_SET_MODIFICATION_COUNT = ModificationTracker { openFileSetModificationCount.get().toLong() }

    private fun registerEditor(editor: EditorEx, project: Project) {
      editor.addFocusListener(object : FocusChangeListener {
        private var managerRef: WeakReference<FileEditorManagerImpl?>? = null

        override fun focusGained(editor1: Editor) {
          if (!Registry.`is`("editor.maximize.on.focus.gained.if.collapsed.in.split", false)) {
            return
          }
          var manager = if (managerRef == null) null else managerRef!!.get()
          if (manager == null) {
            val fileEditorManager = getInstance(project)
            if (fileEditorManager is FileEditorManagerImpl) {
              manager = fileEditorManager
              managerRef = WeakReference(manager)
            }
            else {
              return
            }
          }
          var component: Component? = editor1.component
          while (component != null && component !== manager.mainSplitters) {
            val parent: Component = component.parent
            if (parent is Splitter) {
              if (parent.firstComponent === component &&
                  (parent.proportion == parent.getMinProportion(true) ||
                   parent.proportion == parent.minimumProportion) || parent.proportion == parent.getMinProportion(false) ||
                  parent.proportion == parent.maximumProportion) {
                val pairs = getSplittersToMaximize(project, editor1.component)
                for ((s, second) in pairs) {
                  s.proportion = if (second) s.maximumProportion else s.minimumProportion
                }
                break
              }
            }
            component = parent
          }
        }
      }, project)
    }

    fun isDumbAware(editor: FileEditor): Boolean {
      return java.lang.Boolean.TRUE == editor.getUserData(DUMB_AWARE) &&
             (editor !is PossiblyDumbAware || (editor as PossiblyDumbAware).isDumbAware)
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
    fun forbidSplitFor(file: VirtualFile): Boolean {
      return java.lang.Boolean.TRUE == file.getUserData(SplitAction.FORBID_TAB_SPLIT)
    }

    private fun assertDispatchThread() {
      ApplicationManager.getApplication().assertIsDispatchThread()
    }

    internal fun getOriginalFile(file: VirtualFile): VirtualFile {
      return BackedVirtualFile.getOriginFileIfBacked(if (file is VirtualFileWindow) file.delegate else file)
    }

    fun runBulkTabChange(splitters: EditorsSplitters, task: Runnable) {
      if (!ApplicationManager.getApplication().isDispatchThread) {
        task.run()
      }
      else {
        splitters.insideChange++
        try {
          task.run()
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


  override fun getDockContainer(): DockContainer? = dockable

  internal class MyEditorFactoryListener : EditorFactoryListener {
    init {
      if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor as? EditorEx ?: return
      val project = editor.project
      if (project == null || project.isDisposed) {
        return
      }
      registerEditor(editor, project)
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
    fileToUpdateTitle = null
    Disposer.dispose(dockable)
  }

  private fun dumbModeFinished(project: Project) {
    for (file in openedFiles) {
      val composites = getAllComposites(file)
      val existingProviders = composites.flatMap(EditorComposite::allProviders)
      val existingIds = existingProviders.mapTo(HashSet()) { it.editorTypeId }
      val newProviders = FileEditorProviderManager.getInstance().getProviderList(project, file)
      val toOpen = newProviders.filter { !existingIds.contains(it.editorTypeId) }
      // need to open additional non dumb-aware editors
      for (composite in composites) {
        for (provider in toOpen) {
          composite.addEditor(provider.createEditor(project, file), provider)
        }
      }
      updateFileBackgroundColor(file)
    }

    // update for non-dumb-aware EditorTabTitleProviders
    updateFileName(null)
  }

  @RequiresEdt
  fun initDockableContentFactory() {
    if (contentFactory != null) {
      return
    }
    contentFactory = DockableEditorContainerFactory(this)
    DockManager.getInstance(project).register(DockableEditorContainerFactory.TYPE, contentFactory!!, this)
  }

  //-------------------------------------------------------------------------------
  override fun getComponent(): JComponent = mainSplitters

  fun getAllSplitters(): Set<EditorsSplitters> {
    val all = LinkedHashSet<EditorsSplitters>()
    all.add(mainSplitters)
    for (container in DockManager.getInstance(project).containers) {
      if (container is DockableEditorTabbedContainer) {
        all.add(container.splitters)
      }
    }
    return Collections.unmodifiableSet(all)
  }

  private fun getActiveSplittersAsync(): CompletableFuture<EditorsSplitters?> {
    val result = CompletableFuture<EditorsSplitters?>()
    val fm = IdeFocusManager.getInstance(project)
    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
    fm.doWhenFocusSettlesDown({
                                if (project.isDisposed) {
                                  result.complete(null)
                                  return@doWhenFocusSettlesDown
                                }
                                val focusOwner = fm.focusOwner
                                val container = DockManager.getInstance(project).getContainerFor(focusOwner) { obj ->
                                  DockableEditorTabbedContainer::class.java.isInstance(obj)
                                }
                                if (container is DockableEditorTabbedContainer) {
                                  result.complete(container.splitters)
                                }
                                else {
                                  result.complete(mainSplitters)
                                }
                              }, ModalityState.defaultModalityState())
    return result
  }

  private val activeSplittersSync: EditorsSplitters
    get() {
      assertDispatchThread()
      if (Registry.`is`("ide.navigate.to.recently.focused.editor", false)) {
        val splitters = getAllSplitters().toMutableList()
        if (!splitters.isEmpty()) {
          splitters.sortWith(Comparator { o1, o2 ->
            o2.lastFocusGainedTime.compareTo(o1.lastFocusGainedTime)
          })
          return splitters[0]
        }
      }

      val fm = IdeFocusManager.getInstance(project)
      var focusOwner = fm.focusOwner
                       ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                       ?: fm.getLastFocusedFor(fm.lastFocusedIdeWindow)
      var container = DockManager.getInstance(project).getContainerFor(focusOwner) { obj -> obj is DockableEditorTabbedContainer }
      if (container == null) {
        focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        container = DockManager.getInstance(project).getContainerFor(focusOwner) { obj -> obj is DockableEditorTabbedContainer }
      }
      return if (container is DockableEditorTabbedContainer) {
        container.splitters
      }
      else mainSplitters
    }

  @RequiresReadLock
  override fun getPreferredFocusedComponent(): JComponent? {
    val window = splitters.currentWindow
    if (window != null) {
      val composite = window.selectedComposite
      if (composite != null) {
        return composite.preferredFocusedComponent
      }
    }
    return null
  }

  //-------------------------------------------------------
  /**
   * @return color of the `file` which corresponds to the
   * file's status
   */
  fun getFileColor(file: VirtualFile): Color {
    val fileStatusManager = FileStatusManager.getInstance(project) ?: return UIUtil.getLabelForeground()
    return fileStatusManager.getStatus(file).color ?: UIUtil.getLabelForeground()
  }

  open fun isProblem(file: VirtualFile): Boolean = false

  open fun getFileTooltipText(file: VirtualFile, window: EditorWindow): @NlsContexts.Tooltip String {
    var prefix = ""
    val composite = window.getComposite(file)
    if (composite != null && composite.isPreview) {
      prefix = LangBundle.message("preview.editor.tab.tooltip.text") + " "
    }
    val availableProviders = DumbService.getDumbAwareExtensions(project, EditorTabTitleProvider.EP_NAME)
    for (provider in availableProviders) {
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
    for (each in getAllSplitters()) {
      each.updateFileColor(file)
    }
  }

  private fun updateFileBackgroundColor(file: VirtualFile) {
    if (ExperimentalUI.isNewUI()) {
      return
    }
    for (each in getAllSplitters()) {
      each.updateFileBackgroundColorAsync(file)
    }
  }

  /**
   * Reset the preview tab flag if an internal document change is made.
   */
  private fun resetPreviewFlag(file: VirtualFile) {
    if (!FileDocumentManager.getInstance().isFileModified(file)) {
      return
    }

    for (splitter in getAllSplitters()) {
      for (c in splitter.getAllComposites(file)) {
        if (c.isPreview) {
          c.isPreview = false
        }
      }
      splitter.updateFileColor(file)
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
   * Updates tab title and tab tool tip for the specified `file`
   */
  fun updateFileName(file: VirtualFile?) {
    // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
    // only the last event makes sense to handle
    updateFileTitleAlarm.cancelAndRequest()
    fileToUpdateTitle = SoftReference(file)
  }

  private fun updateFrameTitle() {
    getActiveSplittersAsync().thenAccept { it?.updateFileName(null) }
  }

  @Suppress("removal")
  override fun getFile(editor: FileEditor): VirtualFile? {
    val editorComposite = getComposite(editor)
    val tabFile = editorComposite?.file
    val editorFile = editor.file
    if (editorFile != tabFile) {
      if (editorFile == null) {
        LOG.warn("${editor.javaClass.name}.getFile() shall not return null")
      }
      else if (tabFile == null) {
        //todo DaemonCodeAnalyzerImpl#getSelectedEditors calls it for any Editor
        //LOG.warn(editor.getClass().getName() + ".getFile() shall be used, fileEditor is not opened in a tab.");
      }
      else {
        LOG.warn("fileEditor.getFile=$editorFile != fileEditorManager.getFile=$tabFile, fileEditor.class=${editor.javaClass.name}")
      }
    }
    return tabFile
  }

  override fun unsplitWindow() {
    activeSplittersSync.currentWindow?.unsplit(true)
  }

  override fun unsplitAllWindow() {
    activeSplittersSync.currentWindow?.unsplitAll()
  }

  override fun getWindowSplitCount(): Int = activeSplittersSync.splitCount

  override fun hasSplitOrUndockedWindows(): Boolean {
    val splitters = getAllSplitters()
    return if (splitters.size > 1) true else windowSplitCount > 1
  }

  override fun getWindows(): Array<EditorWindow> {
    val windows = ArrayList<EditorWindow>()
    for (each in getAllSplitters()) {
      windows.addAll(each.getWindows())
    }
    return windows.toTypedArray()
  }

  override fun getNextWindow(window: EditorWindow): EditorWindow? {
    val windows = splitters.getOrderedWindows()
    for (i in windows.indices) {
      if (windows[i] == window) {
        return windows.get((i + 1) % windows.size)
      }
    }
    LOG.error("Not window found")
    return null
  }

  override fun getPrevWindow(window: EditorWindow): EditorWindow? {
    val windows = splitters.getOrderedWindows()
    for (i in windows.indices) {
      if (windows[i] == window) {
        return windows.get((i + windows.size - 1) % windows.size)
      }
    }
    LOG.error("Not window found")
    return null
  }

  override fun createSplitter(orientation: Int, window: EditorWindow?) {
    // window was available from action event, for example when invoked from the tab menu of an editor that is not the 'current'
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

  override fun isInSplitter(): Boolean {
    val currentWindow = splitters.currentWindow
    return currentWindow != null && currentWindow.inSplitter()
  }

  override fun hasOpenedFile(): Boolean = splitters.currentWindow?.selectedComposite != null

  override fun getCurrentFile(): VirtualFile? {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedFile()
    }
    return activeSplittersSync.currentFile
  }

  override fun getActiveWindow(): CompletableFuture<EditorWindow?> = getActiveSplittersAsync().thenApply { it?.currentWindow }

  override fun getCurrentWindow(): EditorWindow? {
    if (!isCurrentlyUnderLocalId) {
      return null
    }
    if (!ApplicationManager.getApplication().isDispatchThread) {
      LOG.warn("Requesting getCurrentWindow() on BGT, returning null", Throwable())
      return null
    }
    return activeSplittersSync.currentWindow
  }

  override fun setCurrentWindow(window: EditorWindow?) {
    if (isCurrentlyUnderLocalId) {
      activeSplittersSync.setCurrentWindow(window = window, requestFocus = true)
    }
  }

  fun closeFile(file: VirtualFile, window: EditorWindow, transferFocus: Boolean) {
    assertDispatchThread()
    openFileSetModificationCount.incrementAndGet()
    CommandProcessor.getInstance().executeCommand(project, {
      if (window.isFileOpen(file)) {
        window.closeFile(file = file, disposeIfNeeded = true, transferFocus = transferFocus)
      }
    }, IdeBundle.message("command.close.active.editor"), null)
    removeSelectionRecord(file, window)
  }

  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    closeFile(file, window, true)
  }

  //============================= EditorManager methods ================================
  override fun closeFile(file: VirtualFile) {
    closeFile(file = file, moveFocus = true, closeAllCopies = false)
  }

  @RequiresEdt
  fun closeFile(file: VirtualFile, moveFocus: Boolean, closeAllCopies: Boolean) {
    if (!closeAllCopies) {
      if (isCurrentlyUnderLocalId) {
        CommandProcessor.getInstance().executeCommand(project, {
          openFileSetModificationCount.incrementAndGet()
          val activeSplitters = activeSplittersSync
          runBulkTabChange(activeSplitters) { activeSplitters.closeFile(file, moveFocus) }
        }, "", null)
      }
      else {
        clientFileEditorManager?.closeFile(file, false)
      }
    }
    else {
      withClientId(localId).use {
        CommandProcessor.getInstance().executeCommand(project, {
          openFileSetModificationCount.incrementAndGet()
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

  private val allClientFileEditorManagers: List<ClientFileEditorManager>
    get() = project.getServices(ClientFileEditorManager::class.java, ClientKind.REMOTE)

  override fun isFileOpenWithRemotes(file: VirtualFile): Boolean {
    return isFileOpen(file) || allClientFileEditorManagers.any { it.isFileOpen(file) }
  }

  //-------------------------------------- Open File ----------------------------------------
  override fun openFileWithProviders(file: VirtualFile,
                                     focusEditor: Boolean,
                                     searchForSplitter: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    val openOptions = FileEditorOpenOptions(requestFocus = focusEditor, reuseOpen = searchForSplitter)
    return openFileWithProviders(file = file, suggestedWindow = null, options = openOptions).retrofit()
  }

  override fun openFileWithProviders(file: VirtualFile,
                                     focusEditor: Boolean,
                                     window: EditorWindow): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileWithProviders(file, window, FileEditorOpenOptions(requestFocus = focusEditor)).retrofit()
  }

  override fun openFileWithProviders(file: VirtualFile,
                                     suggestedWindow: EditorWindow?,
                                     options: FileEditorOpenOptions): FileEditorComposite {
    var window = suggestedWindow
    require(file.isValid) { "file is not valid: $file" }
    assertDispatchThread()
    if (window != null && window.isDisposed) {
      window = null
    }
    if (window == null) {
      val mode = getOpenMode(IdeEventQueue.getInstance().trueCurrentEvent)
      if (mode == OpenMode.NEW_WINDOW) {
        if (forbidSplitFor(file)) {
          closeFile(file)
        }
        return (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(file, this)
      }
      if (mode == OpenMode.RIGHT_SPLIT) {
        val result = openInRightSplit(file)
        if (result != null) {
          return result
        }
      }
    }
    var windowToOpenIn = window
    if (windowToOpenIn == null && (options.reuseOpen || !getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER))) {
      windowToOpenIn = findWindowInAllSplitters(file)
    }
    if (windowToOpenIn == null) {
      windowToOpenIn = getOrCreateCurrentWindow(file)
    }
    return openFileImpl2(windowToOpenIn, file, options)
  }

  private fun findWindowInAllSplitters(file: VirtualFile): EditorWindow? {
    val activeCurrentWindow = activeSplittersSync.currentWindow
    if (activeCurrentWindow != null && isFileOpenInWindow(file, activeCurrentWindow)) {
      return activeCurrentWindow
    }
    for (splitters in getAllSplitters()) {
      for (window in splitters.getWindows()) {
        if (isFileOpenInWindow(file, window)) {
          return if (getBoolean(EDITOR_OPEN_INACTIVE_SPLITTER)) window else activeCurrentWindow
          // return a window from here so that we don't look for it again in getOrCreateCurrentWindow
        }
      }
    }
    return null
  }

  private fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val uiSettings = UISettings.getInstance()
    val useMainWindow = uiSettings.openTabsInMainWindow || SINGLETON_EDITOR_IN_WINDOW.get(selectedEditor, false)
    val splitters = if (useMainWindow) mainSplitters else splitters
    val currentWindow = splitters.currentWindow
    if (currentWindow == null || uiSettings.editorTabPlacement != UISettings.TABS_NONE) {
      return splitters.getOrCreateCurrentWindow(file)
    }
    else {
      return currentWindow
    }
  }

  fun openFileInNewWindow(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    if (forbidSplitFor(file)) {
      closeFile(file)
    }
    return (DockManager.getInstance(project) as DockManagerImpl).createNewDockContainerFor(file, this).retrofit()
  }

  private fun openInRightSplit(file: VirtualFile): FileEditorComposite? {
    val active = splitters
    val window = active.currentWindow
    if (window == null || window.inSplitter() && file == window.selectedFile && file == ArrayUtil.getLastElement(window.files)) {
      // already in right splitter
      return null
    }

    val split = active.openInRightSplit(file) ?: return null
    var result: FileEditorComposite? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val editorsWithProviders = split.composites.flatMap(EditorComposite::allEditorsWithProviders).toList()
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
  @Deprecated("Use public API.")
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
    val result = Ref<FileEditorComposite>()
    CommandProcessor.getInstance().executeCommand(project, { result.set(openFileImpl4(window, file, null, options)) }, "", null)
    return result.get()
  }

  /**
   * @param file    to be opened. Unlike openFile method, file can be
   * invalid. For example, all file where invalidate, and they are being
   * removed one by one. If we have removed one invalid file, then another
   * invalid file become selected. That's why we do not require that
   * passed file is valid.
   * @param entry   map between FileEditorProvider and FileEditorState. If this parameter
   */
  fun openFileImpl3(window: EditorWindow,
                    file: VirtualFile,
                    focusEditor: Boolean,
                    entry: HistoryEntry?): FileEditorComposite {
    return openFileImpl4(window = window, _file = file, entry = entry, options = FileEditorOpenOptions(requestFocus = focusEditor))
  }

  protected val clientFileEditorManager: ClientFileEditorManager?
    get() {
      val clientId = current
      LOG.assertTrue(!clientId.isLocal, "Trying to get ClientFileEditorManager for local ClientId")
      return getProjectSession(project, clientId)?.getService(ClientFileEditorManager::class.java)
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
    if (!isCurrentlyUnderLocalId) {
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
    val compositeRef = Ref<FileEditorComposite>()
    if (!options.isReopeningOnStartup) {
      EdtInvocationManager.invokeAndWaitIfNeeded { compositeRef.set(window.getComposite(file)) }
    }
    val newProviders: List<FileEditorProvider>?
    val builders: Array<AsyncFileEditorProvider.Builder?>?
    if (compositeRef.isNull) {
      if (!canOpenFile(file)) {
        return EMPTY
      }

      // File is not opened yet. In this case we have to create editors
      // and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProviderList(project, file)
      builders = arrayOfNulls(newProviders.size)
      for (i in newProviders.indices) {
        try {
          val provider = newProviders[i]
          builders[i] = ReadAction.compute<AsyncFileEditorProvider.Builder?, RuntimeException> {
            if (project.isDisposed || !file.isValid) {
              return@compute null
            }
            LOG.assertTrue(provider.accept(project, file), "Provider $provider doesn't accept file $file")
            if (provider is AsyncFileEditorProvider) provider.createEditorAsync(project, file) else null
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          LOG.error(e)
        }
        catch (e: AssertionError) {
          LOG.error(e)
        }
      }
    }
    else {
      newProviders = null
      builders = null
    }
    ApplicationManager.getApplication().invokeAndWait {
      if (project.isDisposed || !file.isValid) {
        return@invokeAndWait
      }
      runBulkTabChange(window.owner) {
        compositeRef.set(openFileImpl4Edt(window = window,
                                          file = file,
                                          entry = entry,
                                          options = options,
                                          newProviders = newProviders,
                                          builders = builders?.asList() ?: emptyList()))
      }
    }
    return compositeRef.get()
  }

  private fun openFileImpl4Edt(window: EditorWindow,
                               file: VirtualFile,
                               entry: HistoryEntry?,
                               options: FileEditorOpenOptions,
                               newProviders: List<FileEditorProvider>?,
                               builders: List<AsyncFileEditorProvider.Builder?>): FileEditorComposite {
    @Suppress("NAME_SHADOWING")
    var options = options
    (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
    LOG.assertTrue(file.isValid, "Invalid file: $file")
    if (options.requestFocus) {
      val activeProject = getActiveProject()
      if (activeProject != null && activeProject != project) {
        // allow focus switching only within a project
        options = options.clone().withRequestFocus(false)
      }
    }
    if (entry != null && entry.isPreview) {
      options = options.clone().withUsePreviewTab()
    }
    return doOpenInEdtImpl(window = window, file = file, entry = entry, options = options, newProviders = newProviders, builders = builders)
  }

  private fun doOpenInEdtImpl(window: EditorWindow,
                              file: VirtualFile,
                              entry: HistoryEntry?,
                              options: FileEditorOpenOptions,
                              newProviders: List<FileEditorProvider>?,
                              builders: List<AsyncFileEditorProvider.Builder?>): FileEditorComposite {
    var composite = window.getComposite(file)
    val newEditor = composite == null
    if (newEditor) {
      LOG.assertTrue(newProviders != null)
      composite = createComposite(file, newProviders!!, builders) ?: return EMPTY
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
    val provider = if (entry == null) getInstanceImpl().getSelectedFileEditorProvider(composite)
    else entry.selectedProvider
    if (provider != null) {
      composite.setSelectedEditor(provider.editorTypeId)
    }

    // notify editors about selection changes
    val splitters = window.owner
    splitters.setCurrentWindow(window, options.requestFocus)
    splitters.afterFileOpen(file)
    addSelectionRecord(file, window)
    val selectedEditor = composite.selectedEditor
    selectedEditor.selectNotify()

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
      openFileSetModificationCount.incrementAndGet()
    }

    //[jeka] this is a hack to support back-forward navigation
    // previously here was incorrect call to fireSelectionChanged() with a side-effect
    val ideDocumentHistory = IdeDocumentHistory.getInstance(project)
    (ideDocumentHistory as IdeDocumentHistoryImpl).onSelectionChanged()

    // update frame and tab title
    updateFileName(file)

    // make back/forward work
    ideDocumentHistory.includeCurrentCommandAsNavigation()
    if (options.pin != null) {
      window.setFilePinned(file, options.pin!!)
    }
    if (newEditor) {
      val messageBus = project.messageBus
      messageBus.syncPublisher(FileOpenedSyncListener.TOPIC).fileOpenedSync(this, file, editorsWithProviders)
      @Suppress("DEPRECATION")
      messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpenedSync(this, file, editorsWithProviders)
      notifyPublisher {
        if (isFileOpen(file)) {
          project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(this, file)
        }
      }
    }
    return composite
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
      catch (e: Exception) {
        LOG.error(e)
      }
      catch (e: AssertionError) {
        LOG.error(e)
      }
    }
    return createComposite(file, editorsWithProviders)
  }

  protected fun createComposite(file: VirtualFile,
                                editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      editor.addPropertyChangeListener(editorPropertyChangeListener)
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
    }
    return createCompositeInstance(file, editorsWithProviders)
  }

  @Contract("_, _ -> new")
  protected fun createCompositeInstance(file: VirtualFile,
                                        editorsWithProviders: List<FileEditorWithProvider>): EditorComposite? {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.createComposite(file, editorsWithProviders)
    }
    // the only place this class in created, won't be needed when we get rid of EditorWithProviderComposite usages
    @Suppress("DEPRECATION")
    return EditorWithProviderComposite(file, editorsWithProviders, this)
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
      // We have to try to get state from the history only in case
      // if editor is not opened. Otherwise, history entry might have a state
      // out of sync with the current editor state.
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
    val done = CompletableFuture<Any>()
    busyObject.execute {
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
        runnable.run()
        done.complete(null)
      }, ModalityState.current())
      done
    }
  }

  override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) {
    if (!isCurrentlyUnderLocalId) {
      clientFileEditorManager?.setSelectedEditor(file, fileEditorProviderId)
      return
    }

    val composite = getComposite(file) ?: return
    composite.setSelectedEditor(fileEditorProviderId)
    // todo move to setSelectedEditor()?
    composite.selectedEditor.selectNotify()
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

    val result = SmartList<FileEditor>()
    var selectedEditor: FileEditor? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val file = realDescriptor.file
      val openOptions = FileEditorOpenOptions()
        .withReuseOpen(!realDescriptor.isUseCurrentWindow)
        .withUsePreviewTab(realDescriptor.isUsePreviewTab)
        .withRequestFocus(focusEditor)
      val editors = openFileWithProviders(file = file, suggestedWindow = null, options = openOptions).allEditors
      result.addAll(editors)
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

  override fun getSelectedTextEditor(): Editor? = getSelectedTextEditor(false)

  fun getSelectedTextEditor(isLockFree: Boolean): Editor? {
    if (!isCurrentlyUnderLocalId) {
      val selectedEditor = (clientFileEditorManager ?: return null).getSelectedEditor()
      return if (selectedEditor is TextEditor) selectedEditor.editor else null
    }
    val editor = IntentionPreviewUtils.getPreviewEditor()
    if (editor != null) {
      return editor
    }
    if (!isLockFree) {
      assertDispatchThread()
    }
    val currentWindow = if (isLockFree) mainSplitters.currentWindow else splitters.currentWindow
    if (currentWindow != null) {
      val selectedEditor = currentWindow.selectedComposite
      if (selectedEditor != null && selectedEditor.selectedEditor is TextEditor) {
        return (selectedEditor.selectedEditor as TextEditor).editor
      }
    }
    return null
  }

  override fun isFileOpen(file: VirtualFile): Boolean {
    if (!isCurrentlyUnderLocalId) {
      return (clientFileEditorManager ?: return false).isFileOpen(file)
    }
    return openedComposites.any { it.file == file }
  }

  override fun getOpenFiles(): Array<VirtualFile> = VfsUtilCore.toVirtualFileArray(openedFiles)

  val openedFiles: List<VirtualFile>
    get() {
      if (!isCurrentlyUnderLocalId) {
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
    if (!isCurrentlyUnderLocalId) {
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
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditors()?.toTypedArray() ?: FileEditor.EMPTY_ARRAY
    }
    val selectedEditors = SmartHashSet<FileEditor>()
    for (splitters in getAllSplitters()) {
      splitters.addSelectedEditorsTo(selectedEditors)
    }
    return selectedEditors.toTypedArray()
  }

  override fun getSplitters(): EditorsSplitters {
    return if (ApplicationManager.getApplication().isDispatchThread) activeSplittersSync else mainSplitters
  }

  override fun getSelectedEditor(): FileEditor? {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getSelectedEditor()
    }

    val window = splitters.currentWindow
    if (window != null) {
      val selected = window.selectedComposite
      if (selected != null) {
        return selected.selectedEditor
      }
    }
    return super.getSelectedEditor()
  }

  override fun getSelectedEditor(file: VirtualFile): FileEditor? {
    return getSelectedEditorWithProvider(file)?.fileEditor
  }

  @RequiresEdt
  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? {
    val composite = getComposite(file)
    return composite?.selectedWithProvider
  }

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
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getComposite(file)
    }

    val editorWindow = splitters.currentWindow
    if (editorWindow != null) {
      val composite = editorWindow.getComposite(file)
      if (composite != null) {
        return composite
      }
    }
    val originalFile = getOriginalFile(file)
    return getAllSplitters()
      .asSequence()
      .map { each -> each.getAllComposites(originalFile).firstOrNull { it.file == originalFile } }
      .firstOrNull { it != null }
  }

  fun getAllComposites(file: VirtualFile): List<EditorComposite> {
    if (!isCurrentlyUnderLocalId) {
      return clientFileEditorManager?.getAllComposites(file) ?: ArrayList()
    }
    val result = ArrayList<EditorComposite>()
    for (each in getAllSplitters()) {
      result.addAll(each.getAllComposites(file))
    }
    return result
  }

  override fun getAllEditors(): Array<FileEditor> {
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

  @ApiStatus.Internal
  @RequiresEdt
  fun init(): EditorsSplitters {
    FileStatusManager.getInstance(project)?.addFileStatusListener(MyFileStatusListener(), project)
    val connection = project.messageBus.connect(this)
    connection.subscribe(FileTypeManager.TOPIC, MyFileTypeListener())
    if (!LightEdit.owns(project)) {
      connection.subscribe(ProjectTopics.PROJECT_ROOTS, MyRootsListener())
      connection.subscribe(AdditionalLibraryRootsListener.TOPIC, MyRootsListener())
    }

    // updates tabs names
    connection.subscribe(VirtualFileManager.VFS_CHANGES, MyVirtualFileListener())

    // extends/cuts number of opened tabs. Also updates location of tabs
    connection.subscribe(UISettingsListener.TOPIC, MyUISettingsListener())
    return mainSplitters
  }

  override fun getState(): Element? {
    val state = Element("state")
    mainSplitters.writeExternal(state)
    return state
  }

  override fun loadState(state: Element) {
    mainSplitters.readExternal(state)
  }

  open fun getComposite(editor: FileEditor): EditorComposite? {
    for (splitters in getAllSplitters()) {
      val editorsComposites = splitters.getAllComposites()
      for (i in editorsComposites.indices.reversed()) {
        val composite = editorsComposites[i]
        if (composite.allEditors.contains(editor)) {
          return composite
        }
      }
    }
    for (clientManager in allClientFileEditorManagers) {
      val composite = clientManager.getComposite(editor)
      if (composite != null) {
        return composite
      }
    }
    return null
  }

  @ApiStatus.Internal
  fun fireSelectionChanged(newSelectedComposite: EditorComposite?) {
    val composite = SoftReference.dereference(lastSelectedComposite)
    val oldEditorWithProvider = composite?.selectedWithProvider
    val newEditorWithProvider = newSelectedComposite?.selectedWithProvider
    lastSelectedComposite = if (newSelectedComposite == null) null else WeakReference(newSelectedComposite)
    if (oldEditorWithProvider == newEditorWithProvider) {
      return
    }

    val event = FileEditorManagerEvent(this, oldEditorWithProvider, newEditorWithProvider)
    val publisher = project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
    if (newEditorWithProvider != null) {
      val component = newEditorWithProvider.fileEditor.component
      val holder = ComponentUtil.getParentOfType(EditorWindowHolder::class.java as Class<out EditorWindowHolder?>, component as Component)
      val file = newEditorWithProvider.fileEditor.file
      if (holder != null && file != null) {
        addSelectionRecord(file, holder.editorWindow)
      }
    }
    notifyPublisher { publisher.selectionChanged(event) }
  }

  override fun isChanged(editor: EditorComposite): Boolean {
    val fileStatusManager = FileStatusManager.getInstance(project) ?: return false
    val status = fileStatusManager.getStatus(editor.file)
    return status !== FileStatus.UNKNOWN && status !== FileStatus.NOT_CHANGED
  }

  internal fun disposeComposite(composite: EditorComposite) {
    if (!isCurrentlyUnderLocalId) {
      clientFileEditorManager?.removeComposite(composite)
      return
    }

    openedComposites.remove(composite)
    if (allEditors.isEmpty()) {
      @Suppress("UsePropertyAccessSyntax")
      setCurrentWindow(null)
    }
    if (composite == lastSelected) {
      composite.selectedEditor.deselectNotify()
      splitters.setCurrentWindow(null, false)
    }

    val editorsWithProviders = composite.allEditorsWithProviders
    val selectedEditor = composite.selectedEditor
    for (editorWithProvider in editorsWithProviders.asReversed()) {
      val editor = editorWithProvider.fileEditor
      val provider = editorWithProvider.provider
      // we already notified the myEditor (when fire event)
      if (selectedEditor == editor) {
        editor.deselectNotify()
      }
      editor.removePropertyChangeListener(editorPropertyChangeListener)
      provider.disposeEditor(editor)
    }
    Disposer.dispose(composite)
  }

  private val lastSelected: EditorComposite?
    get() = activeSplittersSync.currentWindow?.selectedComposite

  /**
   * Closes deleted files. Closes file which are in the deleted directories.
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
        assertDispatchThread()
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

  override fun isInsideChange(): Boolean = splitters.isInsideChange

  private inner class MyEditorPropertyChangeListener : PropertyChangeListener {
    @RequiresEdt
    override fun propertyChange(e: PropertyChangeEvent) {
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
          val composite = getComposite(e.source as FileEditor)
          if (composite != null) {
            closeFile(composite.file)
          }
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
      assertDispatchThread()
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

  private inner class MyRootsListener : ModuleRootListener, AdditionalLibraryRootsListener {
    override fun rootsChanged(event: ModuleRootEvent) {
      changeHappened()
    }

    private fun changeHappened() {
      AppUIExecutor
        .onUiThread(ModalityState.any())
        .expireWith(project)
        .submit(Callable { windows.flatMap(EditorWindow::allComposites) })
        .onSuccess(Consumer { allEditors ->
          ReadAction
            .nonBlocking(Callable { calcEditorReplacements(allEditors) })
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState(), Consumer(::replaceEditors))
            .coalesceBy(this)
            .submit(AppExecutorUtil.getAppExecutorService())
        })
    }

    private fun calcEditorReplacements(composites: List<EditorComposite>): Map<EditorComposite, Pair<VirtualFile, Int>> {
      val swappers = EditorFileSwapper.EP_NAME.extensionList
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

    private fun replaceEditors(replacements: Map<EditorComposite, Pair<VirtualFile, Int>>) {
      if (replacements.isEmpty()) {
        return
      }

      for (eachWindow in windows) {
        val selected = eachWindow.selectedComposite
        val composites = eachWindow.allComposites
        for (i in composites.indices) {
          val composite = composites[i]
          val file = composite.file
          if (!file.isValid) {
            continue
          }

          val newFilePair = replacements.get(composite) ?: continue
          val newFile = newFilePair.first ?: continue

          // already open
          if (eachWindow.findFileIndex(newFile) != -1) continue
          val openResult = openFileImpl2(window = eachWindow,
                                         file = newFile,
                                         options = FileEditorOpenOptions(index = i, requestFocus = composite === selected))
          if (newFilePair.second != null) {
            val openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(openResult.allEditors)
            if (openedEditor != null) {
              openedEditor.editor.caretModel.moveToOffset(newFilePair.second!!)
              openedEditor.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
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
      changeHappened()
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
      updateFrameTitle()
    }
  }

  @RequiresEdt
  override fun closeAllFiles() {
    CommandProcessor.getInstance().executeCommand(project, {
      openFileSetModificationCount.incrementAndGet()
      val splitters = splitters
      runBulkTabChange(splitters, splitters::closeAllFiles)
    }, "", null)
  }

  override fun getSiblings(file: VirtualFile): Collection<VirtualFile> = openedFiles

  fun queueUpdateFile(file: VirtualFile) {
    queue.queue(object : Update(file) {
      override fun run() {
        if (isFileOpen(file)) {
          updateFileIcon(file)
          updateFileColor(file)
          updateFileBackgroundColor(file)
          resetPreviewFlag(file)
        }
      }
    })
  }

  override fun getSplittersFor(component: Component): EditorsSplitters {
    val dockContainer = DockManager.getInstance(project).getContainerFor(component) { it is DockableEditorTabbedContainer }
    return if (dockContainer is DockableEditorTabbedContainer) dockContainer.splitters else mainSplitters
  }

  fun getSelectionHistory(): List<Pair<VirtualFile, EditorWindow>> {
    val copy = ArrayList<Pair<VirtualFile, EditorWindow>>()
    for (pair in selectionHistory) {
      if (pair.second!!.files.isEmpty()) {
        val windows = pair.second!!.owner.getWindows()
        if (windows.isNotEmpty() && windows[0].files.isNotEmpty()) {
          val p = Pair(pair.first, windows[0])
          if (!copy.contains(p)) {
            copy.add(p)
          }
        }
      }
      else if (!copy.contains(pair)) {
        copy.add(pair)
      }
    }
    selectionHistory.clear()
    selectionHistory.addAll(copy)
    return selectionHistory
  }

  fun addSelectionRecord(file: VirtualFile, window: EditorWindow) {
    val record = Pair(file, window)
    selectionHistory.remove(record)
    selectionHistory.add(0, record)
  }

  fun removeSelectionRecord(file: VirtualFile, window: EditorWindow) {
    selectionHistory.remove(Pair(file, window))
    updateFileName(file)
  }

  override fun getReady(requestor: Any): ActionCallback = busyObject.getReady(requestor)

  override fun refreshIcons() {
    val openedFiles = openedFiles
    for (each in getAllSplitters()) {
      for (file in openedFiles) {
        each.updateFileIcon(file)
      }
    }
  }

  internal suspend fun openFileOnStartup(window: EditorWindow,
                                         virtualFile: VirtualFile,
                                         entry: HistoryEntry?,
                                         options: FileEditorOpenOptions) {
    assert(options.isReopeningOnStartup)

    if (!ClientId.isCurrentlyUnderLocalId) {
      (clientFileEditorManager ?: return).openFile(file = virtualFile, forceCreate = false)
      return
    }

    val file = getOriginalFile(virtualFile)
    val newProviders = FileEditorProviderManager.getInstance().getProvidersAsync(project, file)
    if (!canOpenFile(file, newProviders)) {
      return
    }

    // file is not opened yet - in this case we have to create editors and select the created EditorComposite.

    val builders = ArrayList<AsyncFileEditorProvider.Builder?>(newProviders.size)
    for (provider in newProviders) {
      val builder = try {
        readAction {
          if (!file.isValid) {
            return@readAction null
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
      catch (e: Exception) {
        LOG.error(e)
        null
      }
      catch (e: AssertionError) {
        LOG.error(e)
        null
      }
      builders.add(builder)
    }

    withContext(Dispatchers.EDT) {
      if (!file.isValid) {
        return@withContext
      }

      val splitters = window.owner
      splitters.insideChange++
      try {
        doOpenInEdtImpl(window = window, file = file, entry = entry, options = options, newProviders = newProviders, builders = builders)
      }
      finally {
        splitters.insideChange--
      }
    }
  }
}

private class SimpleBusyObject : BusyObject.Impl() {
  private val busyCount = LongAdder()
  override fun isReady(): Boolean = busyCount.sum() == 0L

  fun execute(runnable: Supplier<CompletableFuture<*>>) {
    busyCount.increment()
    runnable.get().whenComplete { _, _ ->
      busyCount.decrement()
      if (isReady) {
        onReady()
      }
    }
  }
}