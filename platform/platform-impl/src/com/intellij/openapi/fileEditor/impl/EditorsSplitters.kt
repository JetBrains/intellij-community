// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty", "PrivatePropertyName")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.ClientFileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.IconUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBRectangle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import java.lang.ref.Reference
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds

private val OPEN_FILES_ACTIVITY = Key.create<Activity>("open.files.activity")
private val LOG = logger<EditorsSplitters>()
private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"
private val OPENED_IN_BULK = Key.create<Boolean>("EditorSplitters.opened.in.bulk")

@Suppress("LeakingThis", "IdentifierGrammar")
@DirtyUI
open class EditorsSplitters internal constructor(
  val manager: FileEditorManagerImpl,
  internal val coroutineScope: CoroutineScope,
) : JPanel(BorderLayout()), UISettingsListener {
  companion object {
    const val SPLITTER_KEY: @NonNls String = "EditorsSplitters"

    fun stopOpenFilesActivity(project: Project) {
      project.getUserData(OPEN_FILES_ACTIVITY)?.let { activity ->
        activity.end()
        project.putUserData(OPEN_FILES_ACTIVITY, null)
      }
    }

    internal fun isOpenedInBulk(file: VirtualFile): Boolean = file.getUserData(OPENED_IN_BULK) != null

    @JvmStatic
    fun findDefaultComponentInSplitters(project: Project?): JComponent? {
      return getSplittersToFocus(project)?.currentWindow?.selectedComposite?.preferredFocusedComponent
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

  private val currentWindowFlow = MutableStateFlow<EditorWindow?>(null)
  internal val currentCompositeFlow: MutableStateFlow<EditorComposite?> = MutableStateFlow(null)

  val currentWindow: EditorWindow?
    get() = currentWindowFlow.value

  internal var lastFocusGainedTime: Long = 0L
    private set

  private val windows = CopyOnWriteArraySet<EditorWindow>()

  // temporarily used during initialization of non-main editor splitters
  private val state = AtomicReference<EditorSplitterState?>()

  @JvmField
  internal var insideChange: Int = 0

  private val iconUpdateChannel: MergingUpdateChannel<VirtualFile> = MergingUpdateChannel(delay = 200.milliseconds) { toUpdate ->
    for (file in toUpdate) {
      doUpdateFileIcon(file)
    }
  }

  val currentFile: VirtualFile?
    get() = currentCompositeFlow.value?.file

  private fun showEmptyText(): Boolean = (currentWindow?.getFileSequence() ?: emptySequence()).none()

  val openFileList: List<VirtualFile>
    get() {
      return windows.asSequence()
        .flatMap { window -> window.getComposites().map { it.file } }
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
    val l = PropertyChangeListener { e ->
      val propName = e.propertyName
      if (propName == "Editor.background" || propName == "Editor.foreground" || propName == "Editor.shortcutForeground") {
        repaint()
      }
    }
    UIManager.getDefaults().addPropertyChangeListener(l)
    coroutineScope.coroutineContext.job.invokeOnCompletion { UIManager.getDefaults().removePropertyChangeListener(l) }
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

    coroutineScope.launch(CoroutineName("EditorSplitters frame title update")) {
      currentCompositeFlow.collectLatest { composite ->
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
    setCurrentWindowAndComposite(null)
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
    val componentCount = componentCount
    if (componentCount == 0) {
      return
    }

    val component = getComponent(0)
    try {
      element.addContent(writePanel(component))
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  private fun writePanel(component: Component): Element {
    return when (component) {
      is Splitter -> {
        val result = Element("splitter")
        result.setAttribute("split-orientation", if (component.orientation) "vertical" else "horizontal")
        result.setAttribute("split-proportion", component.proportion.toString())
        val first = Element("split-first")
        first.addContent(writePanel(component.firstComponent))
        val second = Element("split-second")
        second.addContent(writePanel(component.secondComponent))
        result.addContent(first)
        result.addContent(second)
        result
      }
      is JBTabs -> {
        val result = Element("leaf")
        ClientProperty.get(component.component, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY)?.let { limit ->
          result.setAttribute(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), limit.toString())
        }
        findWindowWith(component)?.let { writeWindow(result, it) }
        result
      }
      else -> throw IllegalArgumentException(component.javaClass.name)
    }
  }

  private fun writeWindow(result: Element, window: EditorWindow) {
    val composites = window.getComposites().toList()
    for (i in composites.indices) {
      val file = window.getFileAt(i)
      result.addContent(writeComposite(composites.get(i), window.isFilePinned(file), window.selectedComposite))
    }
  }

  private fun writeComposite(composite: EditorComposite, pinned: Boolean, selectedEditor: EditorComposite?): Element {
    val fileElement = Element("file")
    composite.currentStateAsHistoryEntry().writeExternal(fileElement, manager.project)
    if (pinned) {
      fileElement.setAttribute(PINNED, "true")
    }
    if (composite != selectedEditor) {
      fileElement.setAttribute(CURRENT_IN_TAB, "false")
    }
    return fileElement
  }

  @Internal
  suspend fun restoreEditors(state: EditorSplitterState) {
    withContext(Dispatchers.EDT) {
      removeAll()
    }
    UiBuilder(this).process(state = state) { add(it, BorderLayout.CENTER) }
    withContext(Dispatchers.EDT) {
      validate()

      for (window in windows) {
        // clear empty splitters
        if (window.tabCount == 0) {
          window.removeFromSplitter()
          window.logEmptyStateIfMainSplitter(cause = EmptyStateCause.CONTEXT_RESTORED)
        }
        else {
          (window.tabbedPane.tabs as JBTabsImpl).revalidateAndRepaint()
        }
      }
    }
  }

  @Internal
  suspend fun createEditors(state: EditorSplitterState) {
    manager.project.putUserData(OPEN_FILES_ACTIVITY, StartUpMeasurer.startActivity(StartUpMeasurer.Activities.EDITOR_RESTORING_TILL_PAINT))
    UiBuilder(this).process(state = state) { add(it, BorderLayout.CENTER) }
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
    val oldWindow = currentWindowFlow.value
    val oldComposite = currentCompositeFlow.value

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
      for (file in window.fileList) {
        window.closeFile(file = file, disposeIfNeeded = false, transferFocus = false)
      }
    }
    // should be not required - later we should add here assert
    if (oldWindow != null) {
      currentWindowFlow.compareAndSet(oldWindow, null)
    }
    if (oldComposite != null) {
      currentCompositeFlow.compareAndSet(oldComposite, null)
    }
  }

  internal fun setCurrentWindowAndComposite(window: EditorWindow?) {
    currentWindowFlow.value = window
    currentCompositeFlow.value = window?.selectedComposite
  }

  fun openFilesAsync(): Job {
    return coroutineScope.launch {
      restoreEditors(state = state.getAndSet(null) ?: return@launch)
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

  internal fun updateFileIcon(file: VirtualFile) {
    iconUpdateChannel.queue(file)
  }

  internal fun updateFileIconImmediately(file: VirtualFile, icon: Icon) {
    for (window in windows) {
      val (composite, index) = window.findCompositeAndIndex(file) ?: continue
      window.tabbedPane.tabs.getTabAt(index).setIcon(decorateFileIcon(composite, icon))
    }
  }

  internal suspend fun doUpdateFileIcon(file: VirtualFile) {
    val icon = readAction {
      IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, manager.project)
    }
    withContext(Dispatchers.EDT) {
      updateFileIconImmediately(file, icon)
    }
  }

  internal fun updateFileColorAsync(file: VirtualFile) {
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

    val colorScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    withContext(Dispatchers.EDT) {
      windows.asSequence()
        .mapNotNull { window ->
          window.findCompositeAndIndex(file)?.let { window to it }
        }
        .forEach { (window, compositeAndIndex) ->
          val manager = manager
          var resultAttributes = TextAttributes()
          var attributes = if (manager.isProblem(file)) colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else null
          if (compositeAndIndex.first.isPreview) {
            val italic = TextAttributes(null, null, null, null, Font.ITALIC)
            attributes = if (attributes == null) italic else TextAttributes.merge(italic, attributes)
          }
          resultAttributes = TextAttributes.merge(resultAttributes, attributes)
          val index = compositeAndIndex.second
          window.setForegroundAt(index, fileColor)
          window.setTextAttributes(index, resultAttributes.apply {
            this.foregroundColor = colorScheme.getColor(foregroundFileColor)
          })
        }
    }
  }

  fun trimToSize() {
    for (window in windows) {
      window.trimToSize(fileToIgnore = window.selectedFile, transferFocus = true)
    }
  }

  fun setTabsPlacement(tabPlacement: Int) {
    for (window in windows) {
      window.setTabsPlacement(tabPlacement)
    }
  }

  fun setTabLayoutPolicy(scrollTabLayout: Int) {
    for (window in windows) {
      window.setTabLayoutPolicy(scrollTabLayout)
    }
  }

  internal suspend fun updateFileName(updatedFile: VirtualFile?) {
    for (window in windows) {
      val composites = withContext(Dispatchers.EDT) {
        // update names for other files with the same name, as it might affect UniqueNameEditorTabTitleProvider
        window.getComposites().filter { updatedFile == null || it.file.nameSequence.contentEquals(updatedFile.nameSequence) }.toList()
      }
      for (composite in composites) {
        val title = readAction {
          EditorTabPresentationUtil.getEditorTabTitle(manager.project, composite.file)
        }
        withContext(Dispatchers.EDT) {
          val index = window.findCompositeIndex(composite)
          if (index != -1) {
            val tab = window.tabbedPane.tabs.getTabAt(index)
            tab.setText(title)
            tab.setTooltipText(if (UISettings.getInstance().showTabsTooltips) manager.getFileTooltipText(composite.file, window) else null)
          }
        }
      }
    }

    updateFrameTitle()
  }

  private suspend fun updateFrameTitle() {
    val project = manager.project
    val frame = getFrame() ?: return
    val file = currentFile
    if (file == null) {
      withContext(Dispatchers.EDT) {
        frame.setFileTitle(null, null)
      }
    }
    else {
      val title = readAction {
        FrameTitleBuilder.getInstance().getFileTitle(project, file)
      }

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
        val index = window.findFileIndex(file)
        if (index != -1) {
          window.tabbedPane.tabs.getTabAt(index).setTabColor(color)
        }
      }
    }
  }

  internal val splitCount: Int
    get() = if (componentCount > 0) getSplitCount(getComponent(0) as JComponent) else 0

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
    get() = getWindowSequence().all { it.isEmptyVisible }

  private fun findNextFile(file: VirtualFile): VirtualFile? {
    for (window in windows) {
      for (fileAt in window.getFileSequence()) {
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
    val windows = windows.filter { window -> window.getComposites().any { it.allEditors.contains(editor) } }
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
      window.closeFile(file = file, composite = composite,
                       disposeIfNeeded = FileEditorManagerImpl.isSingletonFileEditor(composite.selectedEditor))
      if (isProjectOpen && window.tabCount == 0 && !window.isDisposed &&
          nextFile != null && !FileEditorManagerImpl.forbidSplitFor(nextFile)) {
        manager.newEditorComposite(nextFile)?.let {
          window.setComposite(it, moveFocus)
        }
      }
    }

    // cleanup windows with no tabs
    if (isProjectOpen) {
      for (window in windows) {
        if (window.isDisposed) {
          // call to window.unsplit() which might make its sibling disposed
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
      updateFileIcon(file)
      updateFileColorAsync(file)
    }
  }

  fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val windowsPerFile = findWindows(file)
    if (currentWindow == null) {
      if (!windowsPerFile.isEmpty()) {
        setCurrentWindow(window = windowsPerFile[0], requestFocus = false)
      }
      else {
        val anyWindow = windows.firstOrNull()
        if (anyWindow == null) {
          createCurrentWindow()
        }
        else {
          setCurrentWindow(window = anyWindow, requestFocus = false)
        }
      }
    }
    else if (!windowsPerFile.isEmpty()) {
      if (!windowsPerFile.contains(currentWindow)) {
        setCurrentWindow(window = windowsPerFile[0], requestFocus = false)
      }
    }
    return currentWindow!!
  }

  internal fun createCurrentWindow() {
    LOG.assertTrue(currentWindow == null)
    val window = EditorWindow(owner = this, coroutineScope.childScope(CoroutineName("EditorWindow")))
    add(window.component, BorderLayout.CENTER)
    setCurrentWindowAndComposite(window)
  }

  /**
   * Sets the window passed as a current ('focused') window among all splitters.
   * All file openings will be done inside this current window.
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor, currently selected in this window
   */
  internal fun setCurrentWindow(window: EditorWindow?, requestFocus: Boolean) {
    require(window == null || windows.contains(window)) { "$window is not a member of this container" }
    setCurrentWindowAndComposite(window)
    if (window != null && requestFocus) {
      window.requestFocus(forced = true)
    }
  }

  internal fun onDisposeComposite(composite: EditorComposite) {
    if (currentCompositeFlow.value == composite) {
      setCurrentWindowAndComposite(null)
    }
  }

  internal fun addWindow(window: EditorWindow) {
    windows.add(window)
    currentWindowFlow.compareAndSet(null, window)
    currentCompositeFlow.compareAndSet(null, window.selectedComposite)
  }

  internal fun removeWindow(window: EditorWindow) {
    val selectedComposite = window.selectedComposite
    windows.remove(window)
    currentWindowFlow.compareAndSet(window, null)
    currentCompositeFlow.compareAndSet(selectedComposite, null)
  }

  fun containsWindow(window: EditorWindow): Boolean = windows.contains(window)

  @ApiStatus.ScheduledForRemoval
  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getAllComposites()}")
  fun getEditorComposites(): List<EditorWithProviderComposite> {
    return windows.asSequence().flatMap { it.getComposites() }.filterIsInstance<EditorWithProviderComposite>().toList()
  }

  fun getAllComposites(): List<EditorComposite> = windows.flatMap { it.getComposites() }

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getAllComposites(VirtualFile)}", level = DeprecationLevel.ERROR)
  fun findEditorComposites(file: VirtualFile): List<EditorWithProviderComposite> {
    return windows.asSequence().mapNotNull { it.getComposite(file) }.filterIsInstance<EditorWithProviderComposite>().toList()
  }

  @RequiresEdt
  fun getAllComposites(file: VirtualFile): List<EditorComposite> = getWindowSequence().mapNotNull { it.getComposite(file) }.toList()

  private fun findWindows(file: VirtualFile): List<EditorWindow> = windows.filter { it.getComposite(file) != null }

  fun getWindows(): Array<EditorWindow> = windows.toTypedArray()

  internal fun getWindowSequence(): Sequence<EditorWindow> = windows.asSequence()

  // collector for windows in tree ordering: get a root component and traverse splitters tree
  internal fun getOrderedWindows(): MutableList<EditorWindow> {
    val result = ArrayList<EditorWindow>()

    fun collectWindow(component: JComponent) {
      if (component is Splitter) {
        collectWindow(component.firstComponent)
        collectWindow(component.secondComponent)
      }
      else if (component is JPanel || component is JBTabs) {
        findWindowWith(component)?.let {
          result.add(it)
        }
      }
    }

    // get root component and traverse splitters tree
    if (componentCount != 0) {
      collectWindow(getComponent(0) as JComponent)
    }
    LOG.assertTrue(result.size == windows.size)
    return result
  }

  internal fun findWindowWith(component: Component): EditorWindow? {
    return ComponentUtil.getParentOfType(EditorWindowHolder::class.java, component)?.editorWindow
  }

  open val isFloating: Boolean
    get() = false

  private inner class MyFocusWatcher : FocusWatcher() {
    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (cause !is FocusEvent || cause.getID() != FocusEvent.FOCUS_GAINED) {
        return
      }

      if (cause.cause == FocusEvent.Cause.ACTIVATION) {
        // Window activation mistakenly puts focus to editor as 'last focused component in this window'
        // even if you activate the window by clicking some other place (e.g., Project View)
        SwingUtilities.invokeLater {
          if (component!!.isFocusOwner) {
            lastFocusGainedTime = System.currentTimeMillis()
          }
        }
      }
      else {
        lastFocusGainedTime = System.currentTimeMillis()
      }

      // we must update the current selected editor composite because if an editor is split, no events like "tab changed"
      if (component != null) {
        setCurrentWindow(window = findWindowWith(component), requestFocus = false)
      }
    }
  }

  @JvmOverloads
  fun openInRightSplit(file: VirtualFile, requestFocus: Boolean = true): EditorWindow? {
    val window = currentWindow ?: return null
    val parent = window.component.parent
    if (parent is Splitter) {
      val component = parent.secondComponent
      if (component !== window.component) {
        // reuse
        windows.find { SwingUtilities.isDescendingFrom(component, it.component) }?.let { rightSplitWindow ->
          manager.openFile(file = file, window = rightSplitWindow, options = FileEditorOpenOptions(requestFocus = requestFocus))
          return rightSplitWindow
        }
      }
    }
    return window.split(orientation = JSplitPane.HORIZONTAL_SPLIT, forceSplit = true, virtualFile = file, focusNew = requestFocus)
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
  private val fileDropHandler = FileDropHandler(null)

  override fun importData(comp: JComponent, t: Transferable): Boolean {
    if (fileDropHandler.canHandleDrop(t.transferDataFlavors)) {
      fileDropHandler.handleDrop(t, splitters.manager.project, splitters.currentWindow)
      return true
    }
    return false
  }

  override fun canImport(comp: JComponent, transferFlavors: Array<DataFlavor>) = fileDropHandler.canHandleDrop(transferFlavors)
}

@Internal
class EditorSplitterStateSplitter(
  @JvmField val firstSplitter: EditorSplitterState,
  @JvmField val secondSplitter: EditorSplitterState,
  splitterElement: Element,
) {
  @JvmField
  val isVertical: Boolean = splitterElement.getAttributeValue("split-orientation") == "vertical"

  @JvmField
  val proportion: Float = splitterElement.getAttributeValue("split-proportion")?.toFloat() ?: 0.5f
}

@Internal
class EditorSplitterStateLeaf(element: Element) {
  @JvmField
  val files: List<FileEntry>

  @JvmField
  val tabSizeLimit: Int

  class FileEntry(
    @JvmField val pinned: Boolean,
    @JvmField val currentInTab: Boolean,
    @JvmField val history: Element,
  )

  init {
    files = (element.getChildren("file")?.map {
      FileEntry(
        pinned = it.getAttributeBooleanValue(PINNED),
        currentInTab = it.getAttributeValue(CURRENT_IN_TAB, "true").toBoolean(),
        history = it.getChild(HistoryEntry.TAG),
      )
    }) ?: emptyList()
    tabSizeLimit = element.getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString())?.toIntOrNull() ?: -1
  }
}

@Internal
class EditorSplitterState(element: Element) {
  @JvmField
  val splitters: EditorSplitterStateSplitter?
  val leaf: EditorSplitterStateLeaf?

  init {
    val splitterElement = element.getChild("splitter")
    val first = splitterElement?.getChild("split-first")
    val second = splitterElement?.getChild("split-second")

    if (first == null || second == null) {
      splitters = null
      leaf = element.getChild("leaf")?.let { EditorSplitterStateLeaf(it) }
    }
    else {
      splitters = EditorSplitterStateSplitter(firstSplitter = EditorSplitterState(first),
                                              secondSplitter = EditorSplitterState(second),
                                              splitterElement = splitterElement)
      leaf = null
    }
  }
}

private class UiBuilder(private val splitters: EditorsSplitters) {
  suspend fun process(state: EditorSplitterState, addChild: (child: JComponent) -> Unit) {
    val splitState = state.splitters
    if (splitState == null) {
      val leaf = state.leaf
      val files = leaf?.files ?: emptyList()
      val trimmedFiles: List<EditorSplitterStateLeaf.FileEntry>
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
      processFiles(fileEntries = trimmedFiles, tabSizeLimit = leaf?.tabSizeLimit ?: Int.MAX_VALUE, addChild = addChild)
    }
    else {
      val splitter = withContext(Dispatchers.EDT) {
        val splitter = createSplitter(isVertical = splitState.isVertical,
                                      proportion = splitState.proportion,
                                      minProp = 0.1f,
                                      maxProp = 0.9f)
        splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)
        addChild(splitter)
        splitter
      }

      process(state = splitState.firstSplitter, addChild = { splitter.firstComponent = it })
      process(state = splitState.secondSplitter, addChild = { splitter.secondComponent = it })
    }
  }

  private suspend fun processFiles(fileEntries: List<EditorSplitterStateLeaf.FileEntry>,
                                   tabSizeLimit: Int,
                                   addChild: (child: JComponent) -> Unit) {
    coroutineScope {
      val windowDeferred = async(Dispatchers.EDT) {
        val editorWindow = EditorWindow(owner = splitters, splitters.coroutineScope.childScope(CoroutineName("EditorWindow")))
        editorWindow.component.isFocusable = false
        if (tabSizeLimit != 1) {
          editorWindow.tabbedPane.component.putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, tabSizeLimit)
        }
        addChild(editorWindow.component)
        editorWindow
      }

      var focusedFile: VirtualFile? = null
      val fileEditorManager = splitters.manager
      val fileDocumentManager = serviceAsync<FileDocumentManager>()

      fun weight(item: EditorSplitterStateLeaf.FileEntry) = (if (item.currentInTab) 1 else 0)

      // open the selected tab first
      val sorted = fileEntries.withIndex().sortedWith(Comparator { o1, o2 ->
        weight(o2.value) - weight(o1.value)
      })
      for ((index, fileEntry) in sorted) {
        val fileName = fileEntry.history.getAttributeValue(HistoryEntry.FILE_ATTR)
        val activity = StartUpMeasurer.startActivity(PathUtilRt.getFileName(fileName), ActivityCategory.REOPENING_EDITOR)
        val entry = HistoryEntry.createLight(fileEditorManager.project, fileEntry.history)
        val file = entry.filePointer.file
        if (file == null) {
          if (ApplicationManager.getApplication().isUnitTestMode) {
            LOG.error("No file exists: ${entry.filePointer.url}")
          }
          continue
        }

        try {
          file.putUserData(OPENED_IN_BULK, true)

          val newProviders = async(CoroutineName("editor provider computing")) {
            serviceAsync<FileEditorProviderManager>().getProvidersAsync(fileEditorManager.project, file)
          }

          val document = readActionBlocking {
            fileDocumentManager.getDocument(file)
          }

          val clientId = ClientId.current
          if (clientId.isLocal) {
            fileEditorManager.openFileOnStartup(windowDeferred = windowDeferred,
                                                file = file,
                                                entry = entry,
                                                options = FileEditorOpenOptions(
                                                  selectAsCurrent = false,
                                                  pin = fileEntry.pinned,
                                                  index = index,
                                                  usePreviewTab = entry.isPreview,
                                                ),
                                                newProviders = newProviders.await())
          }
          else {
            ClientSessionsManager.getProjectSession(fileEditorManager.project, clientId)
              ?.serviceOrNull<ClientFileEditorManager>()?.openFileAsync(file = file, forceCreate = false, requestFocus = true)
          }

          // This is just to make sure document reference is kept on stack till this point
          // so that a document is available for folding state deserialization in HistoryEntry constructor,
          // and that document will be created only once during file opening
          Reference.reachabilityFence(document)
          if (fileEntry.currentInTab) {
            focusedFile = file
          }
        }
        finally {
          file.putUserData(OPENED_IN_BULK, null)
        }
        activity.end()
      }

      if (focusedFile == null) {
        val manager = splitters.manager.project.serviceAsync<ToolWindowManager>()
        manager.invokeLater {
          if (manager.activeToolWindowId == null) {
            manager.getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
          }
        }
      }

      val window = windowDeferred.await()
      splitters.coroutineScope.launch(Dispatchers.EDT) {
        val composite = focusedFile?.let { window.getComposite(focusedFile) } ?: window.selectedComposite
        if (composite != null) {
          fileEditorManager.addSelectionRecord(composite.file, window)

          // OPENED_IN_BULK is forcing 'JBTabsImpl.addTabWithoutUpdating',
          // so these need to be fired even if the composite is already selected
          window.selectOpenedCompositeOnStartup(composite = composite)
          splitters.setCurrentWindowAndComposite(window = window)
        }
      }
    }
  }
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
    return getLastFocusedSplittersForProject(activeWindow, project ?: (activeWindow as IdeFrame).project)
  }

  val frame = FocusManagerImpl.getInstance().lastFocusedFrame
  if (frame is IdeFrameImpl && frame.isActive) {
    return activeWindow?.let { getLastFocusedSplittersForProject(activeWindow = it, project = frame.getProject()) }
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

private fun getLastFocusedSplittersForProject(activeWindow: Window, project: Project?): EditorsSplitters? {
  val fileEditorManager = (if (project == null || project.isDisposed) null else FileEditorManagerEx.getInstanceEx(project))
                          ?: return null
  return (fileEditorManager as? FileEditorManagerImpl)?.getLastFocusedSplitters() ?: getSplittersForProject(activeWindow, project)
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

private fun decorateFileIcon(composite: EditorComposite, baseIcon: Icon): Icon? {
  val settings = UISettings.getInstance()
  val showAsterisk = settings.markModifiedTabsWithAsterisk && composite.isModified
  val showFileIconInTabs = settings.showFileIconInTabs
  if (!showAsterisk || ExperimentalUI.isNewUI()) {
    return if (showFileIconInTabs) baseIcon else null
  }

  val modifiedIcon = IconUtil.cropIcon(icon = AllIcons.General.Modified, area = JBRectangle(3, 3, 7, 7))
  val result = LayeredIcon(2)
  if (showFileIconInTabs) {
    result.setIcon(baseIcon, 0)
    result.setIcon(modifiedIcon, 1, -modifiedIcon.iconWidth / 2, 0)
  }
  else {
    result.setIcon(EmptyIcon.create(modifiedIcon.iconWidth, baseIcon.iconHeight), 0)
    result.setIcon(modifiedIcon, 1, 0, 0)
  }
  return JBUIScale.scaleIcon(result)
}