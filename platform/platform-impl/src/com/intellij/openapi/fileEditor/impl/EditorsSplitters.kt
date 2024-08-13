// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty", "PrivatePropertyName", "ReplacePutWithAssignment")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.ClientFileEditorManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.text.FileEditorDropHandler
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.openapi.wm.FocusWatcher
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy.getPreferredFocusedComponent
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.platform.fileEditor.parseFileEntry
import com.intellij.platform.fileEditor.writeWindow
import com.intellij.platform.ide.IdeFingerprint
import com.intellij.platform.ide.ideFingerprint
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ExceptionUtil
import com.intellij.util.IconUtil
import com.intellij.util.computeFileIconImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBRectangle
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.jsonDomToXml
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.AWTEventListener
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds

private val OPEN_FILES_ACTIVITY = Key.create<Activity>("open.files.activity")
private val LOG = logger<EditorsSplitters>()
private const val IDE_FINGERPRINT: @NonNls String = "ideFingerprint"

@Suppress("LeakingThis", "IdentifierGrammar")
@DirtyUI
open class EditorsSplitters internal constructor(
  @Internal val manager: FileEditorManagerImpl,
  @JvmField internal val coroutineScope: CoroutineScope,
) : JPanel(BorderLayout()), UISettingsListener {
  companion object {
    const val SPLITTER_KEY: @NonNls String = "EditorsSplitters"

    fun findDefaultComponentInSplitters(project: Project?): JComponent? {
      return getSplittersToFocus(project)?.currentCompositeFlow?.value?.preferredFocusedComponent
    }

    @JvmStatic
    fun focusDefaultComponentInSplittersIfPresent(project: Project): Boolean {
      findDefaultComponentInSplitters(project)?.let {
        // not requestFocusInWindow because if a floating or windowed tool window is deactivated (or, ESC pressed to focus editor),
        // then we should focus our window
        it.requestFocus()
        return true
      }
      return false
    }
  }

  private val _currentWindowFlow = MutableStateFlow<EditorWindow?>(null)
  @JvmField
  internal val currentWindowFlow: StateFlow<EditorWindow?> = _currentWindowFlow.asStateFlow()

  @JvmField
  internal val currentCompositeFlow: StateFlow<EditorComposite?>

  val currentWindow: EditorWindow?
    get() = _currentWindowFlow.value

  internal var lastFocusGainedTime: Long = 0L
    private set

  private var previousFocusGainedTime: Long = 0L

  private val windows = CopyOnWriteArraySet<EditorWindow>()

  // temporarily used during initialization of non-main editor splitters
  private val state = AtomicReference<EditorSplitterState?>()

  @JvmField
  internal var insideChange: Int = 0

  private val iconUpdateChannel: MergingUpdateChannel<VirtualFile> = MergingUpdateChannel(delay = 10.milliseconds) { toUpdate ->
    for (file in toUpdate) {
      updateFileIcon(file)
    }
  }

  internal val currentFile: VirtualFile?
    get() = currentCompositeFlow.value?.file

  private fun showEmptyText(): Boolean = (currentWindow?.files() ?: emptySequence()).none()

  internal val openFileList: List<VirtualFile>
    get() {
      return windows.asSequence()
        .flatMap { window -> window.composites().map { it.file } }
        .distinct()
        .toList()
    }

  val selectedFiles: Array<VirtualFile>
    get() {
      val virtualFiles = VfsUtilCore.toVirtualFileArray(windows.mapNotNull { it.selectedFile })
      currentFile?.let { currentFile ->
        for (i in virtualFiles.indices) {
          if (virtualFiles[i] == currentFile) {
            virtualFiles[i] = virtualFiles[0]
            virtualFiles[0] = currentFile
            break
          }
        }
      }
      return virtualFiles
    }

  init {
    background = JBColor.namedColor("Editor.background", IdeBackgroundUtil.getIdeBackgroundColor())
    val propertyChangeListener = PropertyChangeListener { e ->
      val propName = e.propertyName
      if (propName == "Editor.background" || propName == "Editor.foreground" || propName == "Editor.shortcutForeground") {
        repaint()
      }
    }
    UIManager.getDefaults().addPropertyChangeListener(propertyChangeListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion { UIManager.getDefaults().removePropertyChangeListener(propertyChangeListener) }
    MyFocusWatcher().install(this)

    focusTraversalPolicy = MyFocusTraversalPolicy(this)
    isFocusTraversalPolicyProvider = true
    transferHandler = MyTransferHandler(this)
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) {
          invalidate()
          repaint()
        }
      })
    enableEditorActivationOnEscape()

    coroutineScope.launch(CoroutineName("EditorSplitters file icon update")) {
      iconUpdateChannel.start()
    }

    @Suppress("OPT_IN_USAGE")
    currentCompositeFlow = _currentWindowFlow
      .flatMapLatest { editorWindow ->
        if (editorWindow == null) {
          flowOf(null)
        }
        else {
          val flow = editorWindow.currentCompositeFlow
          if (flow.value == null) {
            LOG.debug {
              "editor window is selected but no editor composite yet, please avoid such selection (editorWindow=$editorWindow)"
            }
          }
          flow
        }
      }
      .stateIn(coroutineScope, SharingStarted.Lazily, null)

    coroutineScope.launch(CoroutineName("EditorSplitters frame title update")) {
      @Suppress("OPT_IN_USAGE")
      currentCompositeFlow
        .debounce(100.milliseconds)
        .collectLatest { composite ->
          // some providers depend on editor list (DiffEditorTabTitleProvider)
          composite?.waitForAvailableWithoutTriggeringInit()
          updateFrameTitle()
        }
    }
  }

  fun clear() {
    val windows = windows.toList()
    this.windows.clear()
    for (window in windows) {
      window.dispose()
    }

    removeAll()
    setCurrentWindow(window = null)
    // revalidate doesn't repaint correctly after "Close All"
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    if (showEmptyText()) {
      val gg = IdeBackgroundUtil.withFrameBackground(g, this)
      super.paintComponent(gg)
      g.color = JBColor.border()
      g.drawLine(0, 0, width, 0)
    }
  }

  fun writeExternal(element: Element) {
    writeExternal(element = element, delayedStates = emptyMap())
  }

  internal fun writeExternal(element: Element, delayedStates: Map<EditorComposite, FileEntry>) {
    val componentCount = componentCount
    if (componentCount == 0) {
      return
    }

    val component = getComponent(0)
    try {
      element.addContent(writePanel(component, delayedStates))
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  @Internal
  suspend fun restoreEditors(state: EditorSplitterState, requestFocus: Boolean = true) {
    withContext(Dispatchers.EDT) {
      removeAll()
    }

    UiBuilder(this, isLazyComposite = false).process(state = state, requestFocus = requestFocus) { add(it, BorderLayout.CENTER) }
    withContext(Dispatchers.EDT) {
      validate()

      for (window in windows) {
        // clear empty splitters
        if (window.tabCount == 0) {
          window.removeFromSplitter()
          window.logEmptyStateIfMainSplitter(cause = EmptyStateCause.CONTEXT_RESTORED)
        }
        else {
          window.tabbedPane.editorTabs.revalidateAndRepaint()
        }
      }
    }
  }

  internal suspend fun createEditors(state: EditorSplitterState) {
    manager.project.putUserData(OPEN_FILES_ACTIVITY, StartUpMeasurer.startActivity(StartUpMeasurer.Activities.EDITOR_RESTORING_TILL_PAINT))
    UiBuilder(splitters = this, isLazyComposite = System.getProperty("idea.delayed.editor.composite", "true").toBoolean())
      .process(
        state = state,
        requestFocus = true,
        addChild = { add(it, BorderLayout.CENTER) },
      )
  }

  fun addSelectedEditorsTo(result: MutableCollection<FileEditor>) {
    for (window in windows) {
      val editor = window.selectedComposite?.selectedEditor
      if (editor != null && !result.contains(editor)) {
        result.add(editor)
      }
    }

    val currentWindow = currentWindow
    if (currentWindow != null && !windows.contains(currentWindow)) {
      val editor = currentWindow.selectedComposite?.selectedEditor
      if (editor != null && !result.contains(editor)) {
        result.add(editor)
      }
    }
  }

  fun closeAllFiles(repaint: Boolean = true) {
    val oldWindow = currentWindow

    val windows = windows.toList()
    this.windows.clear()
    for (window in windows) {
      window.dispose()
    }
    removeAll()
    // revalidate doesn't repaint correctly after "Close All"
    if (repaint) {
      repaint()
    }

    for (window in windows) {
      for (file in window.files().toList()) {
        window.closeFile(file = file, disposeIfNeeded = false, transferFocus = false)
      }
    }
    // should be not required - later we should add here assert
    if (oldWindow != null) {
      _currentWindowFlow.compareAndSet(oldWindow, null)
    }
  }

  internal fun setCurrentWindow(window: EditorWindow?) {
    LOG.debug {
     "set editor window to $window: ${ExceptionUtil.currentStackTrace()}"
    }
    _currentWindowFlow.value = window
  }

  @Deprecated("Use openFilesAsync(Boolean) instead", ReplaceWith("openFilesAsync(true)"))
  fun openFilesAsync(): Job = openFilesAsync(requestFocus = true)

  fun openFilesAsync(requestFocus: Boolean): Job {
    return coroutineScope.launch {
      restoreEditors(state = state.getAndSet(null) ?: return@launch, requestFocus = requestFocus)
    }
  }

  internal fun readExternal(element: Element) {
    state.set(EditorSplitterState(element))
  }

  fun getSelectedEditors(): Array<FileEditor> {
    val windows = HashSet(windows)
    currentWindow?.let {
      windows.add(it)
    }
    val editors = windows.mapNotNull { it.selectedComposite?.selectedEditor }
    return if (editors.isEmpty()) FileEditor.EMPTY_ARRAY else editors.toTypedArray()
  }

  internal fun scheduleUpdateFileIcon(file: VirtualFile) {
    iconUpdateChannel.queue(file)
  }

  internal fun updateFileIconImmediately(file: VirtualFile, icon: Icon) {
    val uiSettings = UISettings.getInstance()
    for (window in windows) {
      val (composite, tab) = window.findCompositeAndTab(file) ?: continue
      tab.setIcon(decorateFileIcon(composite = composite, baseIcon = icon, uiSettings = uiSettings))
    }
  }

  internal suspend fun updateFileIcon(file: VirtualFile) {
    val icon = readAction {
      IconUtil.computeFileIcon(file = file, flags = Iconable.ICON_FLAG_READ_STATUS, project = manager.project)
    }
    withContext(Dispatchers.EDT) {
      updateFileIconImmediately(file, icon)
    }
  }

  internal fun scheduleUpdateFileColor(file: VirtualFile) {
    coroutineScope.launch {
      updateFileColor(file)
    }
  }

  internal suspend fun updateFileColor(file: VirtualFile) {
    if (windows.isEmpty()) {
      return
    }

    val (fileColor, foregroundFileColor) = readAction {
      manager.getFileColor(file) to getForegroundColorForFile(manager.project, file)
    }

    val colorScheme = serviceAsync<EditorColorsManager>().schemeForCurrentUITheme
    val attributes = if (manager.isProblem(file)) colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else null
    withContext(Dispatchers.EDT) {
      for ((composite, tab) in windows().mapNotNull { it.findCompositeAndTab(file) }) {
        applyTabColor(
          composite = composite,
          attributes = attributes,
          tab = tab,
          fileColor = fileColor,
          colorScheme = colorScheme,
          foregroundFileColor = foregroundFileColor,
        )
      }
    }
  }

  fun trimToSize() {
    for (window in windows) {
      window.trimToSize(fileToIgnore = window.selectedFile, transferFocus = true)
    }
  }

  internal fun setTabPlacement(tabPlacement: Int) {
    for (window in windows) {
      window.tabbedPane.setTabPlacement(tabPlacement)
    }
  }

  internal fun setTabLayoutPolicy(scrollTabLayout: Int) {
    for (window in windows) {
      window.tabbedPane.setTabLayoutPolicy(scrollTabLayout)
    }
  }

  internal suspend fun updateFrameTitle() {
    val project = manager.project
    val frame = getFrame() ?: return
    val file = currentCompositeFlow.value?.file
    if (file == null) {
      withContext(Dispatchers.EDT) {
        frame.setFileTitle(null, null)
      }
    }
    else {
      val title = serviceAsync<FrameTitleBuilder>().getFileTitleAsync(project, file)
      val ioFile = try {
        if (file is LightVirtualFileBase) null else Path.of(file.presentableUrl)
      }
      catch (ignored: InvalidPathException) {
        null
      }
      withContext(Dispatchers.EDT) {
        frame.setFileTitle(title, ioFile)
      }
    }
  }

  private fun getFrame(): IdeFrameEx? {
    val frame = (ComponentUtil.findUltimateParent(this) as? Window) ?: return null
    return if (frame is IdeFrameEx) frame else ProjectFrameHelper.getFrameHelper(frame as Window?)
  }

  internal val isInsideChange: Boolean
    get() = insideChange > 0

  internal fun updateFileBackgroundColorAsync(file: VirtualFile) {
    coroutineScope.launch(ModalityState.any().asContextElement()) {
      updateFileBackgroundColor(file)
    }
  }

  internal suspend fun updateFileBackgroundColor(file: VirtualFile) {
    val color = readAction {
      EditorTabPresentationUtil.getEditorTabBackgroundColor(manager.project, file)
    }

    withContext(Dispatchers.EDT) {
      for (window in windows) {
        window.findTabByFile(file)?.setTabColor(color)
      }
    }
  }

  internal val splitCount: Int
    get() = if (componentCount > 0) getSplitCount(getComponent(0) as JComponent) else 0

  internal open val isSingletonEditorInWindow: Boolean
    get() = false

  internal open fun afterFileClosed(file: VirtualFile) {}

  open fun afterFileOpen(file: VirtualFile) {}

  fun getTabsAt(point: RelativePoint): JBTabs? {
    val thisPoint = point.getPoint(this)
    var c = SwingUtilities.getDeepestComponentAt(this, thisPoint.x, thisPoint.y)
    while (c != null) {
      if (c is JBTabs) {
        return c
      }
      c = c.parent
    }
    return null
  }

  val isEmptyVisible: Boolean
    get() = windows().all { it.isEmptyVisible }

  private fun findNextFile(file: VirtualFile): VirtualFile? {
    for (window in windows) {
      for (fileAt in window.files()) {
        if (fileAt != file) {
          return fileAt
        }
      }
    }
    return null
  }

  fun closeFile(file: VirtualFile, moveFocus: Boolean) {
    closeFileInWindows(file = file, windows = findWindows(file), moveFocus = moveFocus)
  }

  internal fun closeFileEditor(file: VirtualFile, editor: FileEditor, moveFocus: Boolean) {
    // we can't close individual tab in EditorComposite
    val windows = windows.filter { window ->
      window.composites().any { composite ->
        composite.allEditorsWithProviders.any { it.fileEditor == editor }
      }
    }
    closeFileInWindows(file = file, windows = windows, moveFocus = moveFocus)
  }

  private fun closeFileInWindows(file: VirtualFile, windows: List<EditorWindow>, moveFocus: Boolean) {
    if (windows.isEmpty()) {
      return
    }

    val isProjectOpen = manager.project.isOpen

    val nextFile = findNextFile(file)
    for (window in windows) {
      val composite = window.getComposite(file) ?: continue
      window.closeFile(
        file = file,
        composite = composite,
        disposeIfNeeded = isSingletonFileEditor(composite.selectedEditor),
      )
      if (isProjectOpen &&
          window.tabCount == 0 &&
          !window.isDisposed &&
          nextFile != null && !FileEditorManagerImpl.forbidSplitFor(nextFile)) {
        manager.createCompositeAndModel(file = nextFile, window = window)?.let {
          window.addComposite(
            composite = it,
            file = it.file,
            options = FileEditorOpenOptions(requestFocus = moveFocus, usePreviewTab = it.isPreview),
            isNewEditor = true,
          )
        }
      }
    }

    // cleanup windows with no tabs
    if (isProjectOpen) {
      for (window in windows) {
        if (window.isDisposed) { // call to window.unsplit() which might make its sibling disposed
          continue
        }
        if (window.tabCount == 0) {
          window.unsplit(setCurrent = false)
        }
      }
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    for (window in windows) {
      window.updateTabsVisibility(uiSettings)
    }
    if (!manager.project.isOpen) {
      return
    }

    for (file in openFileList) {
      updateFileBackgroundColorAsync(file)
      scheduleUpdateFileIcon(file)
      scheduleUpdateFileColor(file)
    }
  }

  internal fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val windowsPerFile = findWindows(file)
    if (currentWindow == null) {
      if (!windowsPerFile.isEmpty()) {
        setCurrentWindow(window = windowsPerFile[0])
      }
      else {
        val anyWindow = windows.firstOrNull()
        if (anyWindow == null) {
          createCurrentWindow()
        }
        else {
          setCurrentWindow(window = anyWindow)
        }
      }
    }
    else if (!windowsPerFile.isEmpty() && !windowsPerFile.contains(currentWindow)) {
      setCurrentWindow(window = windowsPerFile[0])
    }
    return currentWindow!!
  }

  internal fun createCurrentWindow() {
    LOG.assertTrue(currentWindow == null)
    val window = EditorWindow(owner = this, coroutineScope.childScope("EditorWindow"))
    add(window.component, BorderLayout.CENTER)
    windows.add(window)
    setCurrentWindow(window)
  }

  /**
   * Sets the window passed as a current ('focused') window among all splitters.
   * All file openings will be done inside this current window.
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor, currently selected in this window
   */
  internal fun setCurrentWindow(window: EditorWindow?, requestFocus: Boolean) {
    require(window == null || windows.contains(window)) { "$window is not a member of this container" }
    setCurrentWindow(window)
    if (window != null && requestFocus) {
      window.requestFocus(forced = true)
    }
  }

  internal fun onDisposeComposite(composite: EditorComposite) {
    if (currentCompositeFlow.value == composite) {
      setCurrentWindow(_currentWindowFlow.value)
    }
  }

  internal fun addWindow(window: EditorWindow) {
    windows.add(window)
  }

  internal fun removeWindow(window: EditorWindow) {
    windows.remove(window)
    _currentWindowFlow.compareAndSet(window, null)
  }

  internal fun containsWindow(window: EditorWindow): Boolean = windows.contains(window)

  fun getAllComposites(): List<EditorComposite> = windows.flatMap { it.composites() }

  @RequiresEdt
  fun getAllComposites(file: VirtualFile): List<EditorComposite> = windows().mapNotNull { it.getComposite(file) }.toList()

  private fun findWindows(file: VirtualFile): List<EditorWindow> = windows.filter { it.getComposite(file) != null }

  fun getWindows(): Array<EditorWindow> = windows.toTypedArray()

  @Internal
  fun windows(): Sequence<EditorWindow> = windows.asSequence()

  // collector for windows in tree ordering: get a root component and traverse splitters tree
  internal fun getOrderedWindows(): MutableList<EditorWindow> {
    val result = ArrayList<EditorWindow>()

    fun collectWindow(component: JComponent) {
      if (component is Splitter) {
        collectWindow(component.firstComponent)
        collectWindow(component.secondComponent)
      }
      else if (component is EditorWindowHolder) {
        result.add(component.editorWindow)
      }
    }

    // get root component and traverse splitters tree
    if (componentCount != 0) {
      collectWindow(getComponent(0) as JComponent)
    }
    LOG.assertTrue(result.size == windows.size)
    return result
  }

  open val isFloating: Boolean
    get() = false

  private inner class MyFocusWatcher : FocusWatcher() {
    init {
      val focusRequestListener = object : AWTEventListener {
        override fun eventDispatched(event: AWTEvent?) {
          if (event is FocusEvent && event.getID() == FocusEvent.FOCUS_GAINED) {
            rollbackFocusGainedIfNecessary(event.source as Component)
          }
        }
      }
      Toolkit.getDefaultToolkit().addAWTEventListener(focusRequestListener, AWTEvent.FOCUS_EVENT_MASK)
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        Toolkit.getDefaultToolkit().removeAWTEventListener(focusRequestListener)
      }
    }

    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (component == null || cause !is FocusEvent || cause.getID() != FocusEvent.FOCUS_GAINED) {
        return
      }

      if (cause.cause == FocusEvent.Cause.ACTIVATION) {
        // The window has just become active. Focus may immediately move away if activation was caused by a click outside the editor.
        previousFocusGainedTime = lastFocusGainedTime
        lastFocusGainedTime = System.currentTimeMillis()
      }
      else {
        lastFocusGainedTime = System.currentTimeMillis()
        previousFocusGainedTime = 0L
      }

      // we must update the current selected editor composite because if an editor is split, no events like "tab changed"
      ComponentUtil.getParentOfType(EditorWindowHolder::class.java, component)?.editorWindow?.let {
        setCurrentWindow(it)
      }
    }

    private fun rollbackFocusGainedIfNecessary(newFocusedComponent: Component) {
      // We need to detect the following situation:
      // 1. The user clicks an inactive IDE window.
      // 2. The window gets focus, the last focused editor becomes focused.
      // 3. The component that the user clicked becomes focused immediately after that.
      // In this case, the IDE should behave as if the editor never received this last focus to begin with.
      // However, it's impossible to detect when the editor gains focus.
      // At that point, the mouse click may not even be in the event queue yet,
      // and it's anyone's guess whether the window was activated by a mouse click or, say, with Alt+Tab.
      // Therefore, we update the editor's last focused time anyway, but then roll back if this situation is detected.
      if (previousFocusGainedTime == 0L) return // Nothing to roll back.
      if (ComponentUtil.getParentOfType(EditorsSplitters::class.java, newFocusedComponent) == this@EditorsSplitters) {
        // The clicked component is a part of these editor splitters, the user actually requested to focus this specific component.
        previousFocusGainedTime = 0L
        return
      }
      val ourWindow = ComponentUtil.getWindow(this@EditorsSplitters)
      if (ourWindow !is IdeFrameImpl) return // This workaround is only for the main IDE window.
      if (!ourWindow.wasJustActivatedByClick) return
      val newFocusedComponentWindow = ComponentUtil.getWindow(newFocusedComponent)
      if (ourWindow != newFocusedComponentWindow) return // We don't care about focus changes in other windows.
      lastFocusGainedTime = previousFocusGainedTime
      previousFocusGainedTime = 0L
    }
  }

  @JvmOverloads
  @RequiresEdt
  fun openInRightSplit(file: VirtualFile, requestFocus: Boolean = true): EditorWindow? {
    val window = currentWindow ?: return null
    val parent = window.component.parent
    if (parent is Splitter) {
      val component = parent.secondComponent
      if (component !== window.component) {
        // reuse
        windows.find { SwingUtilities.isDescendingFrom(component, it.component) }?.let { rightSplitWindow ->
          manager.openFile(
            file = file,
            window = rightSplitWindow,
            options = FileEditorOpenOptions(requestFocus = requestFocus, waitForCompositeOpen = false),
          )
          return rightSplitWindow
        }
      }
    }
    return window.split(orientation = JSplitPane.HORIZONTAL_SPLIT, forceSplit = true, virtualFile = file, focusNew = requestFocus)
  }
}

private fun writePanel(component: Component, delayedStates: Map<EditorComposite, FileEntry>): Element {
  return when (component) {
    is Splitter -> {
      val result = Element("splitter")
      result.setAttribute("split-orientation", if (component.orientation) "vertical" else "horizontal")
      result.setAttribute("split-proportion", component.proportion.toString())
      val first = Element("split-first")
      first.addContent(writePanel(component.firstComponent, delayedStates))
      val second = Element("split-second")
      second.addContent(writePanel(component.secondComponent, delayedStates))
      result.addContent(first)
      result.addContent(second)
      result
    }
    is EditorWindowHolder -> {
      val window = component.editorWindow
      val result = Element("leaf")
      result.setAttribute(IDE_FINGERPRINT, ideFingerprint().asString())

      ClientProperty.get(window.tabbedPane.component, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY)?.let { limit ->
        result.setAttribute(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), limit.toString())
      }
      writeWindow(result = result, window = window, delayedStates = delayedStates)
      result
    }
    else -> throw IllegalArgumentException(component.javaClass.name)
  }
}

private class MyFocusTraversalPolicy(private val splitters: EditorsSplitters) : IdeFocusTraversalPolicy() {
  override fun getDefaultComponent(focusCycleRoot: Container): Component? {
    return splitters.currentCompositeFlow.value?.focusComponent?.let {
      getPreferredFocusedComponent(it, this)
    } ?: getPreferredFocusedComponent(splitters, this)
  }

  override fun getProject() = splitters.manager.project
}

private class MyTransferHandler(private val splitters: EditorsSplitters) : TransferHandler() {
  private val fileDropHandler = FileEditorDropHandler(null)

  override fun importData(comp: JComponent, t: Transferable): Boolean {
    if (fileDropHandler.canHandleDrop(t.transferDataFlavors)) {
      fileDropHandler.handleDrop(t, splitters.manager.project, splitters.currentWindow)
      return true
    }
    return false
  }

  override fun canImport(comp: JComponent, transferFlavors: Array<DataFlavor>) = fileDropHandler.canHandleDrop(transferFlavors)
}

internal class EditorSplitterStateSplitter(
  @JvmField val firstSplitter: EditorSplitterState,
  @JvmField val secondSplitter: EditorSplitterState,
  splitterElement: Element,
) {
  @JvmField
  val isVertical: Boolean = splitterElement.getAttributeValue("split-orientation") == "vertical"

  @JvmField
  val proportion: Float = splitterElement.getAttributeValue("split-proportion")?.toFloat() ?: 0.5f
}

internal class EditorSplitterStateLeaf(element: Element, storedIdeFingerprint: IdeFingerprint) {
  @JvmField
  val files: List<FileEntry> = (element.getChildren("file")?.map { parseFileEntry(it, storedIdeFingerprint = storedIdeFingerprint) })
                               ?: emptyList()

  @JvmField
  val tabSizeLimit: Int = element.getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString())?.toIntOrNull() ?: -1
}

@Internal
class EditorSplitterState(element: Element) {
  @JvmField
  internal val splitters: EditorSplitterStateSplitter?
  @JvmField
  internal val leaf: EditorSplitterStateLeaf?

  init {
    val splitterElement = element.getChild("splitter")
    val first = splitterElement?.getChild("split-first")
    val second = splitterElement?.getChild("split-second")

    if (first == null || second == null) {
      splitters = null
      leaf = element.getChild("leaf")?.let { leafElement ->
        EditorSplitterStateLeaf(
          element = leafElement,
          storedIdeFingerprint = (try {
            leafElement.getAttributeValue(IDE_FINGERPRINT)?.let(::IdeFingerprint)
          }
          catch (ignored: NumberFormatException) {
            null
          }) ?: IdeFingerprint(0),
        )
      }
    }
    else {
      splitters = EditorSplitterStateSplitter(
        firstSplitter = EditorSplitterState(first),
        secondSplitter = EditorSplitterState(second),
        splitterElement = splitterElement,
      )
      leaf = null
    }
  }
}

private class UiBuilder(private val splitters: EditorsSplitters, private val isLazyComposite: Boolean) {
  suspend fun process(state: EditorSplitterState, requestFocus: Boolean, addChild: (child: JComponent) -> Unit) {
    val splitState = state.splitters
    if (splitState == null) {
      val leaf = state.leaf
      val files = leaf?.files ?: emptyList()
      val trimmedFiles: List<FileEntry>
      var toRemove = files.size - EditorWindow.tabLimit
      if (toRemove <= 0) {
        trimmedFiles = files
      }
      else {
        trimmedFiles = ArrayList(files.size)
        // trim to EDITOR_TAB_LIMIT, ignoring CLOSE_NON_MODIFIED_FILES_FIRST policy
        for (fileElement in files) {
          if (toRemove <= 0 || fileElement.pinned) {
            trimmedFiles.add(fileElement)
          }
          else {
            toRemove--
          }
        }
      }

      processFiles(
        fileEntries = trimmedFiles,
        tabSizeLimit = leaf?.tabSizeLimit ?: Int.MAX_VALUE,
        addChild = addChild,
        requestFocus = requestFocus,
      )
    }
    else {
      val splitter = withContext(Dispatchers.EDT) {
        val splitter = createSplitter(
          isVertical = splitState.isVertical,
          proportion = splitState.proportion,
          minProp = 0.1f,
          maxProp = 0.9f,
        )
        splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)
        addChild(splitter)
        splitter
      }

      process(state = splitState.firstSplitter, requestFocus = requestFocus) { splitter.firstComponent = it }
      process(state = splitState.secondSplitter, requestFocus = requestFocus) { splitter.secondComponent = it }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun processFiles(
    fileEntries: List<FileEntry>,
    tabSizeLimit: Int,
    addChild: (child: JComponent) -> Unit,
    requestFocus: Boolean,
  ) {
    val fileEditorManager = splitters.manager
    val session = fileEditorManager.project.serviceAsync<ClientSessionsManager<ClientProjectSession>>().getSession(ClientId.current)
    val virtualFileManager = VirtualFileManager.getInstance()
    if (session != null && !session.isLocal) {
      for ((index, fileEntry) in fileEntries.withIndex()) {
        val file = resolveFileOrLogError(fileEntry, virtualFileManager) ?: return
        session.serviceOrNull<ClientFileEditorManager>()?.openFileAsync(
          file = file,
          options = FileEditorOpenOptions(
            selectAsCurrent = fileEntry.currentInTab,
            pin = fileEntry.pinned,
            index = index,
            usePreviewTab = fileEntry.isPreview,
          ),
        )
      }
      return
    }

    val windowCoroutineScope = splitters.coroutineScope.childScope("EditorWindow")

    val windowDeferred = windowCoroutineScope.async(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      splitters.insideChange++
      val editorWindow = EditorWindow(owner = splitters, coroutineScope = windowCoroutineScope)
      editorWindow.component.isFocusable = false
      if (tabSizeLimit != 1) {
        editorWindow.tabbedPane.component.putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, tabSizeLimit)
      }
      editorWindow
    }

    val delayedTasks = ConcurrentLinkedQueue<Job>()
    val items = coroutineScope {
      val placeholderIcon by lazy { EmptyIcon.create(AllIcons.FileTypes.Text) }
      fileEntries.map { fileEntry ->
        async {
          computeFileEntry(
            virtualFileManager = virtualFileManager,
            fileEntry = fileEntry,
            fileEditorManager = fileEditorManager,
            delayedTasks = delayedTasks,
            placeholderIcon = placeholderIcon,
            splitters = splitters,
            isLazyComposite = isLazyComposite,
          )
        }
      }
    }.mapNotNull { it.getCompleted() }

    span("file opening in EDT", Dispatchers.EDT) {
      var window: EditorWindow? = null
      val windowAddedDeferred = CompletableDeferred<Unit>()
      try {
        window = windowDeferred.await()
        fileEditorManager.openFilesOnStartup(
          items = items,
          window = window,
          requestFocus = requestFocus,
          isLazyComposite = isLazyComposite,
          windowAdded = suspend { windowAddedDeferred.await() },
        )
        window.updateTabsVisibility()
        addChild(window.component)
        splitters.addWindow(window)
        windowAddedDeferred.complete(Unit)
      }
      finally {
        splitters.insideChange--
        if (window != null) {
          splitters.validate()

          window.tabbedPane.editorTabs.updateListeners()

          window.coroutineScope.launch {
            for (delayedTask in delayedTasks) {
              delayedTask.start()
            }
          }
        }
      }
    }
  }
}

private fun computeFileEntry(
  virtualFileManager: VirtualFileManager,
  fileEntry: FileEntry,
  fileEditorManager: FileEditorManagerImpl,
  delayedTasks: MutableCollection<Job>,
  placeholderIcon: EmptyIcon,
  splitters: EditorsSplitters,
  isLazyComposite: Boolean,
): FileToOpen? {
  val compositeCoroutineScope = splitters.coroutineScope.childScope("EditorComposite(file=${fileEntry.url})")

  val notFullyPreparedFile = resolveFileOrLogError(fileEntry, virtualFileManager) ?: return null

  // do not expose `file` variable to avoid using it instead of `fileProvider`
  val fileProviderDeferred = compositeCoroutineScope.async(start = if (fileEntry.currentInTab) CoroutineStart.DEFAULT else CoroutineStart.LAZY) {
    // https://youtrack.jetbrains.com/issue/IJPL-157845/Incorrect-encoding-of-file-during-project-opening
    if (notFullyPreparedFile !is VirtualFileWithoutContent && !notFullyPreparedFile.isCharsetSet) {
      blockingContext {
        ProjectLocator.withPreferredProject(notFullyPreparedFile, fileEditorManager.project).use {
          try {
            notFullyPreparedFile.contentsToByteArray(true)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (ignore: FileTooBigException) {
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      }
    }
    notFullyPreparedFile
  }

  val fileProvider = suspend { fileProviderDeferred.await() }

  val model = fileEditorManager.createEditorCompositeModelOnStartup(
    compositeCoroutineScope = compositeCoroutineScope,
    fileProvider = fileProvider,
    fileEntry = fileEntry,
    isLazy = !fileEntry.currentInTab && isLazyComposite,
  )

  val tabTitleTask = compositeCoroutineScope.async(start = CoroutineStart.LAZY) {
    EditorTabPresentationUtil.getEditorTabTitleAsync(fileEditorManager.project, fileProvider())
  }
  val tabIconTask = if (UISettings.getInstance().showFileIconInTabs) {
    compositeCoroutineScope.async(start = CoroutineStart.LAZY) {
      val file = fileProvider()
      readAction {
        computeFileIconImpl(file = file, flags = Iconable.ICON_FLAG_READ_STATUS, project = fileEditorManager.project)
      }
    }
  }
  else {
    null
  }

  val tabFileColorTask = compositeCoroutineScope.async {
    val fileStatusManager = fileEditorManager.project.serviceAsync<FileStatusManager>()
    val file = fileProvider()
    readAction {
      (fileStatusManager.getStatus(file).color ?: UIUtil.getLabelForeground()) to
        getForegroundColorForFile(fileEditorManager.project, file)
    }
  }

  val tabEntry = fileEntry.tab
  val initialTabTitle = if (!tabEntry.tabTitle.isNullOrEmpty() && fileEntry.ideFingerprint == ideFingerprint()) {
    delayedTasks.add(tabTitleTask)
    tabEntry.tabTitle
  }
  else {
    tabTitleTask.start()
    notFullyPreparedFile.presentableName
  }

  val initialTabIcon = if (tabIconTask == null) {
    null
  }
  else {
    val cachedIcon = tabEntry.icon
      ?.takeIf { fileEntry.ideFingerprint == ideFingerprint() }
      ?.let { decodeCachedImageIconFromByteArray(it) }
    if (cachedIcon == null) {
      tabIconTask.start()
    }
    else {
      delayedTasks.add(tabIconTask)
    }

    cachedIcon ?: placeholderIcon
  }

  val tabColorTask = compositeCoroutineScope.async(start = CoroutineStart.LAZY) {
    val colorScheme = serviceAsync<EditorColorsManager>().schemeForCurrentUITheme
    val attributes = if (fileEditorManager.isProblem(notFullyPreparedFile)) colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else null
    val colors = tabFileColorTask.await()

    var effectiveAttributes = if (fileEntry.isPreview) {
      val italic = TextAttributes(null, null, null, null, Font.ITALIC)
      if (attributes == null) italic else TextAttributes.merge(italic, attributes)
    }
    else {
      attributes
    }

    effectiveAttributes = TextAttributes.merge(TextAttributes(), effectiveAttributes).apply {
      foregroundColor = colorScheme.getColor(colors.second)
    }

    colors.first to effectiveAttributes
  }

  val initialTabTextAttributes = if ((tabEntry.textAttributes != null || tabEntry.foregroundColor != null) &&
                                     fileEntry.ideFingerprint == ideFingerprint()) {
    delayedTasks.add(tabColorTask)

    tabEntry.foregroundColor?.let {
      @Suppress("UseJBColor")
      Color(it)
    } to tabEntry.textAttributes?.let { state -> TextAttributes().also { it.readExternal(jsonDomToXml(state)) } }
  }
  else {
    tabColorTask.start()
    null
  }

  return FileToOpen(
    fileEntry = fileEntry,
    scope = compositeCoroutineScope,
    file = notFullyPreparedFile,
    model = model,
    customizer = { tab ->
      tab.setText(initialTabTitle)
      tab.setIcon(initialTabIcon)

      initialTabTextAttributes?.let {
        tab.setDefaultForegroundAndAttributes(foregroundColor = it.first, attributes = it.second)
      }

      compositeCoroutineScope.launch {
        val tooltipText = if (UISettings.getInstance().showTabsTooltips) {
          readAction { fileEditorManager.getFileTooltipText(notFullyPreparedFile, tab.composite) }
        }
        else {
          null
        }
        val title = tabTitleTask.await()
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          tab.setText(title)
          tab.setTooltipText(tooltipText)
        }
      }

      compositeCoroutineScope.launch {
        val (foregroundColor, attributes) = tabColorTask.await()
        withContext(Dispatchers.EDT) {
          tab.setDefaultForegroundAndAttributes(
            foregroundColor = foregroundColor,
            attributes = attributes,
          )
        }
      }

      if (tabIconTask != null) {
        compositeCoroutineScope.launch {
          val icon = tabIconTask.await()
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            tab.setIcon(decorateFileIcon(composite = tab.composite, baseIcon = icon, uiSettings = UISettings.getInstance()))
          }
        }
      }
    },
  )
}

internal data class FileToOpen(
  @JvmField val scope: CoroutineScope,
  @JvmField val file: VirtualFile,
  @JvmField val model: Flow<EditorCompositeModel>,
  @JvmField val fileEntry: FileEntry,
  @JvmField val customizer: (TabInfo) -> Unit,
)

private fun resolveFileOrLogError(fileEntry: FileEntry, virtualFileManager: VirtualFileManager): VirtualFile? {
  val file = virtualFileManager.findFileByUrl(fileEntry.url) ?: virtualFileManager.refreshAndFindFileByUrl(fileEntry.url)
  if (file != null && file.isValid) {
    return file
  }

  val message = "No file exists: ${fileEntry.url}"
  if (ApplicationManager.getApplication().isUnitTestMode) {
    LOG.error(message)
  }
  else {
    LOG.warn(message)
  }
  return null
}

private val ACTIVATE_EDITOR_ON_ESCAPE_HANDLER = KeyEventPostProcessor { e ->
  if (!e.isConsumed && KeymapUtil.isEventForAction(e, IdeActions.ACTION_FOCUS_EDITOR)) {
    var target = e.component
    while (target != null && (target !is Window || target is FloatingDecorator) && target !is EditorsSplitters) {
      target = target.parent
    }
    if (target is IdeFrame) {
      target.project?.let {
        EditorsSplitters.focusDefaultComponentInSplittersIfPresent(it)
        e.consume()
      }
    }
  }
  false
}

private fun enableEditorActivationOnEscape() {
  val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
  kfm.removeKeyEventPostProcessor(ACTIVATE_EDITOR_ON_ESCAPE_HANDLER) // we need only one handler, not one per EditorsSplitters instance
  kfm.addKeyEventPostProcessor(ACTIVATE_EDITOR_ON_ESCAPE_HANDLER)
}

private fun getSplitCount(component: JComponent): Int {
  if (component is Splitter) {
    return getSplitCount(component.firstComponent) + getSplitCount(component.secondComponent)
  }
  return 1
}

private fun getSplittersToFocus(suggestedProject: Project?): EditorsSplitters? {
  var project = suggestedProject
  var activeWindow = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
  if (activeWindow is FloatingDecorator) {
    val lastFocusedFrame = IdeFocusManager.findInstanceByComponent(activeWindow).lastFocusedFrame
    val frameComponent = lastFocusedFrame?.component
    val lastFocusedWindow = if (frameComponent == null) null else SwingUtilities.getWindowAncestor(frameComponent)
    activeWindow = lastFocusedWindow ?: activeWindow
    if (project == null) {
      project = lastFocusedFrame?.project
    }
    return getSplittersForProject(activeWindow = activeWindow, project = project)
  }
  if (activeWindow is IdeFrame.Child) {
    return getSplittersToActivate(activeWindow, project ?: (activeWindow as IdeFrame).project)
  }

  val frame = FocusManagerImpl.getInstance().lastFocusedFrame
  if (frame is IdeFrameImpl && frame.isActive) {
    return activeWindow?.let { getSplittersToActivate(activeWindow = it, project = frame.getProject()) }
  }

  // getSplitters is not implemented in unit test mode
  if (project != null && !project.isDefault && !project.isDisposed && !ApplicationManager.getApplication().isUnitTestMode) {
    return FileEditorManagerEx.getInstanceEx(project).splitters
  }
  return null
}

private fun getSplittersForProject(activeWindow: Window, project: Project?): EditorsSplitters? {
  val fileEditorManager = (if (project == null || project.isDisposed) null else FileEditorManagerEx.getInstanceEx(project)) ?: return null
  return fileEditorManager.getSplittersFor(activeWindow) ?: fileEditorManager.splitters
}

// When the tool window is hidden by a shortcut, we want focus to return to previously focused splitters.
// When it's hidden by clicking on stripe button, we want focus to stay in the current window.
private fun getSplittersToActivate(activeWindow: Window, project: Project?): EditorsSplitters? {
  if (project == null || project.isDisposed) return null
  if (IdeEventQueue.getInstance().trueCurrentEvent is KeyEvent) {
    (FileEditorManagerEx.getInstanceEx(project) as? FileEditorManagerImpl)?.getLastFocusedSplitters()?.let {
      return it
    }
  }
  return getSplittersForProject(activeWindow, project)
}

internal fun createSplitter(isVertical: Boolean, proportion: Float, minProp: Float, maxProp: Float): Splitter {
  return object : Splitter(isVertical, proportion, minProp, maxProp) {
    init {
      setDividerWidth(1)
      setFocusable(false)
    }

    override fun createDivider(): Divider {
      val divider = OnePixelDivider(isVertical, this)
      divider.background = JBColor.namedColor("EditorPane.splitBorder", JBColor.border())
      return divider
    }
  }
}

internal fun decorateFileIcon(composite: EditorComposite, baseIcon: Icon, uiSettings: UISettings): Icon? {
  val showAsterisk = composite.isModified && uiSettings.markModifiedTabsWithAsterisk
  if (!showAsterisk || ExperimentalUI.isNewUI()) {
    return if (uiSettings.showFileIconInTabs) baseIcon else null
  }

  val modifiedIcon = IconUtil.cropIcon(icon = AllIcons.General.Modified, area = JBRectangle(3, 3, 7, 7))
  val result = LayeredIcon(2)
  if (uiSettings.showFileIconInTabs) {
    result.setIcon(baseIcon, 0)
    result.setIcon(modifiedIcon, 1, -modifiedIcon.iconWidth / 2, 0)
  }
  else {
    result.setIcon(EmptyIcon.create(modifiedIcon.iconWidth, baseIcon.iconHeight), 0)
    result.setIcon(modifiedIcon, 1, 0, 0)
  }
  return JBUIScale.scaleIcon(result)
}

private fun applyTabColor(
  composite: EditorComposite,
  attributes: TextAttributes?,
  tab: TabInfo,
  fileColor: Color,
  colorScheme: EditorColorsScheme,
  foregroundFileColor: ColorKey?,
) {
  val effectiveAttributes = if (composite.isPreview) {
    val italic = TextAttributes(null, null, null, null, Font.ITALIC)
    if (attributes == null) italic else TextAttributes.merge(italic, attributes)
  }
  else {
    attributes
  }

  tab.setDefaultForegroundAndAttributes(
    foregroundColor = fileColor,
    attributes = TextAttributes.merge(TextAttributes(), effectiveAttributes).apply {
      this.foregroundColor = colorScheme.getColor(foregroundFileColor)
    },
  )
}

internal fun stopOpenFilesActivity(project: Project) {
  project.getUserData(OPEN_FILES_ACTIVITY)?.let { activity ->
    project.putUserData(OPEN_FILES_ACTIVITY, null)
    activity.end()
  }
}