// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty", "PrivatePropertyName")

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.ClientProperty
import com.intellij.ui.DirtyUI
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.IconUtil
import com.intellij.util.PathUtil
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ContainerEvent
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import java.lang.Runnable
import java.lang.ref.Reference
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds

private val OPEN_FILES_ACTIVITY = Key.create<Activity>("open.files.activity")
private val LOG = logger<EditorsSplitters>()
private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"
private val OPENED_IN_BULK = Key.create<Boolean>("EditorSplitters.opened.in.bulk")

@Suppress("LeakingThis")
@DirtyUI
open class EditorsSplitters internal constructor(val manager: FileEditorManagerImpl) : JPanel(BorderLayout()),
                                                                                       UISettingsListener, Disposable {
  companion object {
    const val SPLITTER_KEY: @NonNls String = "EditorsSplitters"

    fun stopOpenFilesActivity(project: Project) {
      project.getUserData(OPEN_FILES_ACTIVITY)?.let { activity ->
        activity.end()
        project.putUserData(OPEN_FILES_ACTIVITY, null)
      }
    }

    internal fun isOpenedInBulk(file: VirtualFile) = file.getUserData(OPENED_IN_BULK) != null

    @JvmStatic
    fun findDefaultComponentInSplitters(project: Project?): JComponent? {
      return getSplittersToFocus(project)?.currentWindow?.selectedComposite?.preferredFocusedComponent
    }

    @JvmStatic
    fun focusDefaultComponentInSplittersIfPresent(project: Project): Boolean {
      findDefaultComponentInSplitters(project)?.let {
        // not requestFocusInWindow because if floating or windowed tool window is deactivated (or, ESC pressed to focus editor),
        // then we should focus our window
        it.requestFocus()
        return true
      }
      return false
    }
  }

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
    get() = if (currentWindow != null) currentWindow!!.selectedFile else null

  private fun showEmptyText(): Boolean = (currentWindow?.fileSequence ?: emptySequence()).none()

  val openFileList: List<VirtualFile>
    get() {
      return windows.asSequence()
        .flatMap { window -> window.composites.map { it.file } }
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
    Disposer.register(this) { UIManager.getDefaults().removePropertyChangeListener(l) }

    val focusWatcher = MyFocusWatcher()
    focusWatcher.install(this)
    Disposer.register(this) { focusWatcher.deinstall(this) }

    focusTraversalPolicy = MyFocusTraversalPolicy(this)
    isFocusTraversalPolicyProvider = true
    transferHandler = MyTransferHandler(this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        invalidate()
        repaint()
      }
    })
    enableEditorActivationOnEscape()

    @Suppress("DEPRECATION")
    manager.project.coroutineScope.launch(CoroutineName("EditorSplitters file icon update")) {
      iconUpdateChannel.start()
    }.cancelOnDispose(this)
  }

  override fun dispose() {
    dropTarget = null
  }

  fun clear() {
    for (window in windows) {
      window.dispose()
    }

    removeAll()
    windows.clear()
    currentWindow = null
    // revalidate doesn't repaint correctly after "Close All"
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    if (showEmptyText()) {
      val gg = IdeBackgroundUtil.withFrameBackground(g, this)
      super.paintComponent(gg)
      @Suppress("UseJBColor")
      g.color = if (StartupUiUtil.isUnderDarcula()) JBColor.border() else Color(0, 0, 0, 50)
      g.drawLine(0, 0, width, 0)
    }
  }

  fun writeExternal(element: Element) {
    if (componentCount == 0) {
      return
    }

    val panel = getComponent(0) as JPanel
    if (panel.componentCount != 0) {
      try {
        element.addContent(writePanel(panel.getComponent(0)))
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  private fun writePanel(component: Component): Element {
    return when (component) {
      is Splitter -> {
        val result = Element("splitter")
        result.setAttribute("split-orientation", if (component.orientation) "vertical" else "horizontal")
        result.setAttribute("split-proportion", component.proportion.toString())
        val first = Element("split-first")
        first.addContent(writePanel(component.firstComponent.getComponent(0)))
        val second = Element("split-second")
        second.addContent(writePanel(component.secondComponent.getComponent(0)))
        result.addContent(first)
        result.addContent(second)
        result
      }
      is JBTabs -> {
        val result = Element("leaf")
        val limit = ClientProperty.get((component as JBTabs).component, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY)
        if (limit != null) {
          result.setAttribute(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), limit.toString())
        }
        val window = findWindowWith(component)
        window?.let { writeWindow(result, it) }
        result
      }
      else -> {
        throw IllegalArgumentException(component.javaClass.name)
      }
    }
  }

  private fun writeWindow(result: Element, window: EditorWindow) {
    val composites = window.composites.toList()
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

  suspend fun restoreEditors(onStartup: Boolean) {
    restoreEditors(state = state.getAndSet(null) ?: return, onStartup = onStartup, anyEditorOpened = null)
  }

  @Internal
  suspend fun restoreEditors(state: EditorSplitterState, onStartup: Boolean, anyEditorOpened: CompletableDeferred<Unit>?) {
    manager.project.putUserData(OPEN_FILES_ACTIVITY, StartUpMeasurer.startActivity(StartUpMeasurer.Activities.EDITOR_RESTORING_TILL_PAINT))
    val component = UIBuilder(this, anyEditorOpened).process(state, topPanel)
    anyEditorOpened?.complete(Unit)
    withContext(Dispatchers.EDT) {
      runActivity("editor reopening post-processing") {
        component.isFocusable = false
        if (!onStartup) {
          removeAll()
        }
        add(component, BorderLayout.CENTER)
        validate()
        val windows = windows.toList()
        for (window in windows) {
          // clear empty splitters
          if (window.tabCount == 0) {
            window.removeFromSplitter()
          }
          else {
            (window.tabbedPane.tabs as JBTabsImpl).revalidateAndRepaint()
          }
        }

        if (onStartup) {
          val composite = currentWindow?.selectedComposite ?: return@withContext
          val selectedEditor = composite.selectedEditor
          if (selectedEditor is TextEditor) {
            AsyncEditorLoader.performWhenLoaded(selectedEditor.editor) {
              composite.preferredFocusedComponent?.requestFocusInWindow()
            }
          }
          else {
            composite.preferredFocusedComponent?.requestFocusInWindow()
          }
        }
      }
    }
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
    val windows = windows.toList()
    this.windows.clear()
    for (window in windows) {
      window.dispose()
    }
    removeAll()
    currentWindow = null
    // revalidate doesn't repaint correctly after "Close All"
    if (repaint) {
      repaint()
    }

    for (window in windows) {
      for (file in window.fileList) {
        window.closeFile(file = file, disposeIfNeeded = false, transferFocus = false)
      }
    }
  }

  fun openFilesAsync(): Job {
    @Suppress("DEPRECATION")
    return manager.project.coroutineScope.launch {
      restoreEditors(onStartup = false)
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

  fun updateFileIcon(file: VirtualFile) {
    iconUpdateChannel.queue(file)
  }

  internal fun updateFileIconImmediately(file: VirtualFile, icon: Icon) {
    for (window in findWindows(file)) {
      window.updateFileIcon(file, icon)
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

  fun updateFileColor(file: VirtualFile) {
    val windows = findWindows(file)
    if (windows.isEmpty()) {
      return
    }

    val colorScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    for (window in windows) {
      val composite = window.getComposite(file)!!
      val index = window.findCompositeIndex(composite)
      LOG.assertTrue(index != -1)
      val manager = manager
      var resultAttributes = TextAttributes()
      var attributes = if (manager.isProblem(file)) colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else null
      if (composite.isPreview) {
        val italic = TextAttributes(null, null, null, null, Font.ITALIC)
        attributes = if (attributes == null) italic else TextAttributes.merge(italic, attributes)
      }
      resultAttributes = TextAttributes.merge(resultAttributes, attributes)
      window.setForegroundAt(index, manager.getFileColor(file))
      window.setTextAttributes(index, resultAttributes.apply {
        this.foregroundColor = colorScheme.getColor(getForegroundColorForFile(manager.project, file))
      })
    }
  }

  fun trimToSize() {
    for (window in windows) {
      window.trimToSize(window.selectedFile, true)
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

  fun updateFileName(updatedFile: VirtualFile?) {
    for (window in getWindows()) {
      for (file in window.fileSequence) {
        if (updatedFile == null || file.name == updatedFile.name) {
          window.updateFileName(file, window)
        }
      }
    }

    val project = manager.project
    val frame = getFrame(project) ?: return
    val file = currentFile
    if (file == null) {
      frame.setFileTitle(null, null)
    }
    else {
      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
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
  }

  protected open fun getFrame(project: Project): IdeFrameEx? {
    val frame = WindowManagerEx.getInstanceEx().getFrameHelper(project)
    val app = ApplicationManager.getApplication()
    LOG.assertTrue((app.isUnitTestMode || app.isHeadlessEnvironment) || frame != null)
    return frame
  }

  val isInsideChange: Boolean
    get() = insideChange > 0

  internal fun updateFileBackgroundColorAsync(file: VirtualFile) {
    @Suppress("DEPRECATION")
    manager.project.coroutineScope.launch {
      updateFileBackgroundColor(file)
    }
  }

  internal suspend fun updateFileBackgroundColor(file: VirtualFile) {
    val color = readAction {
      EditorTabPresentationUtil.getEditorTabBackgroundColor(manager.project, file)
    }

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      for (window in windows) {
        window.updateFileBackgroundColor(file, color)
      }
    }
  }

  internal val splitCount: Int
    get() = if (componentCount > 0) getSplitCount(getComponent(0) as JPanel) else 0

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
    get() = getWindows().all { it.isEmptyVisible }

  private fun findNextFile(file: VirtualFile): VirtualFile? {
    for (window in windows) {
      for (fileAt in window.fileSequence) {
        if (fileAt != file) {
          return fileAt
        }
      }
    }
    return null
  }

  fun closeFile(file: VirtualFile, moveFocus: Boolean) {
    val windows = findWindows(file)
    closeFileInWindows(file, windows, moveFocus)
  }

  internal fun closeFileEditor(file: VirtualFile, editor: FileEditor, moveFocus: Boolean) {
    // we can't close individual tab in EditorComposite
    val windows = windows.filter { it.composites.any { it.allEditors.contains(editor) } }
    closeFileInWindows(file, windows, moveFocus)
  }

  private fun closeFileInWindows(file: VirtualFile, windows: List<EditorWindow>, moveFocus: Boolean) {
    if (windows.isEmpty()) {
      return
    }

    val isProjectOpen = manager.project.isOpen

    val nextFile = findNextFile(file)
    for (window in windows) {
      LOG.assertTrue(window.selectedComposite != null)
      window.closeFile(file, false, moveFocus)
      if (window.tabCount == 0 && nextFile != null && isProjectOpen && !FileEditorManagerImpl.forbidSplitFor(nextFile)) {
        manager.newEditorComposite(nextFile)?.let {
          window.setComposite(it, moveFocus)
        }
      }
    }
    // cleanup windows with no tabs
    for (window in windows) {
      if (!isProjectOpen || window.isDisposed) {
        // call to window.unsplit() which might make its sibling disposed
        continue
      }
      if (window.tabCount == 0) {
        window.unsplit(false)
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
      updateFileColor(file)
    }
  }

  val topPanel: JPanel?
    get() = if (componentCount > 0) getComponent(0) as JPanel else null

  var currentWindow: EditorWindow? = null
    private set(currentWindow) {
      require(currentWindow == null || windows.contains(currentWindow)) { "$currentWindow is not a member of this container" }
      field = currentWindow
    }

  fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val windows = findWindows(file)
    if (currentWindow == null) {
      if (!windows.isEmpty()) {
        setCurrentWindow(windows[0], false)
      }
      else {
        val anyWindow = this.windows.firstOrNull()
        if (anyWindow == null) {
          createCurrentWindow()
        }
        else {
          setCurrentWindow(window = anyWindow, requestFocus = false)
        }
      }
    }
    else if (!windows.isEmpty()) {
      if (!windows.contains(currentWindow)) {
        setCurrentWindow(window = windows[0], requestFocus = false)
      }
    }
    return currentWindow!!
  }

  internal fun createCurrentWindow() {
    LOG.assertTrue(currentWindow == null)
    currentWindow = createEditorWindow()
    add(currentWindow!!.panel, BorderLayout.CENTER)
  }

  internal fun createEditorWindow() = EditorWindow(owner = this, parentDisposable = this)

  /**
   * sets the window passed as a current ('focused') window among all splitters. All file openings will be done inside this
   * current window
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor currently selected in this window
   */
  fun setCurrentWindow(window: EditorWindow?, requestFocus: Boolean) {
    val newComposite = window?.selectedComposite
    val fireRunnable = Runnable { manager.fireSelectionChanged(newComposite) }
    currentWindow = window
    manager.updateFileName(window?.selectedFile)
    if (window == null) {
      fireRunnable.run()
    }
    else {
      val selectedComposite = window.selectedComposite
      if (selectedComposite != null) {
        fireRunnable.run()
      }
      if (requestFocus) {
        window.requestFocus(true)
      }
    }
  }

  internal fun addWindow(window: EditorWindow) {
    windows.add(window)
    if (currentWindow == null) {
      currentWindow = window
      val selectedComposite = window.selectedComposite ?: return
      manager.updateFileName(selectedComposite.file)
      manager.fireSelectionChanged(window.selectedComposite)
    }
  }

  internal fun removeWindow(window: EditorWindow) {
    windows.remove(window)
    if (currentWindow == window) {
      currentWindow = null
    }
  }

  fun containsWindow(window: EditorWindow): Boolean = windows.contains(window)

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getAllComposites()}")
  fun getEditorComposites(): List<EditorWithProviderComposite> {
    return windows.asSequence().flatMap { it.composites }.filterIsInstance<EditorWithProviderComposite>().toList()
  }

  fun getAllComposites(): List<EditorComposite> = windows.flatMap { it.composites }
  //---------------------------------------------------------

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getAllComposites(VirtualFile)}", level = DeprecationLevel.ERROR)
  fun findEditorComposites(file: VirtualFile): List<EditorWithProviderComposite> {
    return windows.asSequence().mapNotNull { it.getComposite(file) }.filterIsInstance<EditorWithProviderComposite>().toList()
  }

  @RequiresEdt
  fun getAllComposites(file: VirtualFile): List<EditorComposite> = windows.mapNotNull { it.getComposite(file) }

  private fun findWindows(file: VirtualFile): List<EditorWindow> = windows.filter { it.getComposite(file) != null }

  fun getWindows(): Array<EditorWindow> = windows.toTypedArray()

  internal fun getWindowSequence(): Sequence<EditorWindow> = windows.asSequence()

  // Collector for windows in tree ordering:
  // get root component and traverse splitters tree:
  internal fun getOrderedWindows(): MutableList<EditorWindow> {
    val result = ArrayList<EditorWindow>()

    // Collector for windows in tree ordering:
    class WindowCollector {
      fun collect(panel: JPanel) {
        val component = panel.getComponent(0)
        if (component is Splitter) {
          collect(component.firstComponent as JPanel)
          collect(component.secondComponent as JPanel)
        }
        else if (component is JPanel || component is JBTabs) {
          findWindowWith(component)?.let {
            result.add(it)
          }
        }
      }
    }

    // get root component and traverse splitters tree:
    if (componentCount != 0) {
      val comp = getComponent(0)
      LOG.assertTrue(comp is JPanel)
      val panel = comp as JPanel
      if (panel.componentCount != 0) {
        WindowCollector().collect(panel)
      }
    }
    LOG.assertTrue(result.size == windows.size)
    return result
  }

  internal fun findWindowWith(component: Component): EditorWindow? {
    return windows.firstOrNull { SwingUtilities.isDescendingFrom(component, it.panel) }
  }

  open val isFloating: Boolean
    get() = false

  private inner class MyFocusWatcher : FocusWatcher() {
    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (cause is FocusEvent && cause.getID() == FocusEvent.FOCUS_GAINED) {
        if (cause.cause == FocusEvent.Cause.ACTIVATION) {
          // Window activation mistakenly puts focus to editor as 'last focused component in this window'
          // even if you activate the window by clicking some other place (e.g. Project View)
          SwingUtilities.invokeLater {
            if (component!!.isFocusOwner) {
              lastFocusGainedTime = System.currentTimeMillis()
            }
          }
        }
        else {
          lastFocusGainedTime = System.currentTimeMillis()
        }
      }
      var newWindow: EditorWindow? = null
      if (component != null) {
        newWindow = findWindowWith(component)
      }
      else if (cause is ContainerEvent && cause.getID() == ContainerEvent.COMPONENT_REMOVED) {
        // do not change current window in case of child removal as in JTable.removeEditor
        // otherwise Escape in a toolwindow will not focus editor with JTable content
        return
      }
      currentWindow = newWindow
      setCurrentWindow(newWindow, false)
    }
  }

  @JvmOverloads
  fun openInRightSplit(file: VirtualFile, requestFocus: Boolean = true): EditorWindow? {
    val window = currentWindow ?: return null
    val parent = window.panel.parent
    if (parent is Splitter) {
      val component = parent.secondComponent
      if (component !== window.panel) {
        // reuse
        findWindowWith(component)?.let { rightSplitWindow ->
          manager.openFileWithProviders(file, requestFocus, rightSplitWindow)
          return rightSplitWindow
        }
      }
    }
    return window.split(SwingConstants.VERTICAL, true, file, requestFocus)
  }
}

private class MyFocusTraversalPolicy(private val splitters: EditorsSplitters) : IdeFocusTraversalPolicy() {
  override fun getDefaultComponent(focusCycleRoot: Container): Component? {
    return splitters.currentWindow?.selectedComposite?.focusComponent?.let {
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
class EditorSplitterState(element: Element) {
  @JvmField
  val first: Element?

  @JvmField
  val second: Element?

  @JvmField
  val firstSplitter: EditorSplitterState?

  @JvmField
  val secondSplitter: EditorSplitterState?

  @JvmField
  val files: List<FileEntry>

  @JvmField
  val isVertical: Boolean

  @JvmField
  val proportion: Float

  @JvmField
  val tabSizeLimit: Int

  class FileEntry(
    @JvmField val pinned: Boolean,
    @JvmField val currentInTab: Boolean,
    @JvmField val history: Element,
  )

  init {
    val splitterElement = element.getChild("splitter")
    first = splitterElement?.getChild("split-first")
    second = splitterElement?.getChild("split-second")

    firstSplitter = first?.let { EditorSplitterState(it) }
    secondSplitter = second?.let { EditorSplitterState(it) }

    isVertical = splitterElement?.getAttributeValue("split-orientation") == "vertical"
    proportion = splitterElement?.getAttributeValue("split-proportion")?.toFloat() ?: 0.5f

    val leaf = element.getChild("leaf")
    files = (leaf?.getChildren("file")?.map {
      FileEntry(
        pinned = it.getAttributeBooleanValue(PINNED),
        currentInTab = it.getAttributeValue(CURRENT_IN_TAB, "true").toBoolean(),
        history = it.getChild(HistoryEntry.TAG),
      )
    }) ?: emptyList()
    tabSizeLimit = leaf?.getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString())?.toIntOrNull() ?: -1
  }
}

private class UIBuilder(private val splitters: EditorsSplitters, private val firstEditorOpened: CompletableDeferred<Unit>? = null) {
  suspend fun process(state: EditorSplitterState, context: JPanel?): JPanel {
    if (state.firstSplitter != null && state.secondSplitter != null) {
      return processSplitter(state, context)
    }

    val files = state.files
    val trimmedFiles: List<EditorSplitterState.FileEntry>
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
    return processFiles(fileEntries = trimmedFiles, tabSizeLimit = state.tabSizeLimit, context = context)
  }

  private suspend fun processFiles(fileEntries: List<EditorSplitterState.FileEntry>, tabSizeLimit: Int, context: JPanel?): JPanel {
    val window = withContext(Dispatchers.EDT) {
      var editorWindow = context?.let(splitters::findWindowWith)
      if (editorWindow == null) {
        editorWindow = splitters.createEditorWindow()
      }
      else if (splitters.currentWindow == null) {
        splitters.setCurrentWindow(window = editorWindow, requestFocus = false)
      }
      if (tabSizeLimit != 1) {
        editorWindow.tabbedPane.component.putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, tabSizeLimit)
      }
      editorWindow
    }

    var focusedFile: VirtualFile? = null
    val fileEditorManager = splitters.manager
    val fileDocumentManager = FileDocumentManager.getInstance()
    for (i in fileEntries.indices) {
      val fileEntry = fileEntries.get(i)
      val fileName = fileEntry.history.getAttributeValue(HistoryEntry.FILE_ATTR)
      val activity = StartUpMeasurer.startActivity(PathUtil.getFileName(fileName), ActivityCategory.REOPENING_EDITOR)
      val entry = HistoryEntry.createLight(fileEditorManager.project, fileEntry.history)
      val virtualFile = entry.filePointer.file
      if (virtualFile == null) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          LOG.error(InvalidDataException("No file exists: ${entry.filePointer.url}"))
        }
        continue
      }

      val openOptions = FileEditorOpenOptions(
        selectAsCurrent = false,
        pin = fileEntry.pinned,
        index = i,
        isReopeningOnStartup = true,
        usePreviewTab = entry.isPreview,
      )

      try {
        virtualFile.putUserData(OPENED_IN_BULK, true)
        val document = readAction {
          fileDocumentManager.getDocument(virtualFile)
        }
        fileEditorManager.openFileOnStartup(window = window, virtualFile = virtualFile, entry = entry, options = openOptions)
        // This is just to make sure document reference is kept on stack till this point
        // so that document is available for folding state deserialization in HistoryEntry constructor
        // and that document will be created only once during file opening
        Reference.reachabilityFence(document)
        if (fileEntry.currentInTab) {
          focusedFile = virtualFile
        }
      }
      finally {
        virtualFile.putUserData(OPENED_IN_BULK, null)
        firstEditorOpened?.complete(Unit)
      }
      activity.end()
    }

    if (focusedFile == null) {
      val manager = ToolWindowManager.getInstance(splitters.manager.project)
      manager.invokeLater {
        if (manager.activeToolWindowId == null) {
          manager.getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
        }
      }
    }
    else {
      fileEditorManager.addSelectionRecord(focusedFile, window)
      @Suppress("DEPRECATION")
      fileEditorManager.project.coroutineScope.launch(Dispatchers.EDT) {
        window.getComposite(focusedFile)?.let {
          window.setComposite(it, true)
        }
      }
    }
    return window.panel
  }

  suspend fun processSplitter(state: EditorSplitterState, context: JPanel?): JPanel {
    if (context == null) {
      val firstComponent = process(state = state.firstSplitter!!, context = null)
      val secondComponent = process(state = state.secondSplitter!!, context = null)
      return withContext(Dispatchers.EDT) {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        val splitter = createSplitter(orientation = state.isVertical,
                                      proportion = state.proportion,
                                      minProp = 0.1f,
                                      maxProp = 0.9f)
        splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)
        panel.add(splitter, BorderLayout.CENTER)
        splitter.firstComponent = firstComponent
        splitter.secondComponent = secondComponent
        panel
      }
    }

    var firstComponent: JPanel
    var secondComponent: JPanel
    withContext(Dispatchers.EDT) {
      val component = context.getComponent(0)
      if (component is Splitter) {
        firstComponent = component.firstComponent as JPanel
        secondComponent = component.secondComponent as JPanel
      }
      else {
        firstComponent = context
        secondComponent = context
      }
    }
    process(state = state.firstSplitter!!, context = firstComponent)
    process(state = state.secondSplitter!!, context = secondComponent)
    return context
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
  if (component.componentCount <= 0) {
    return 0
  }

  val firstChild = component.getComponent(0) as JComponent
  if (firstChild is Splitter) {
    return getSplitCount(firstChild.firstComponent) + getSplitCount(firstChild.secondComponent)
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
    return getSplittersForProject(activeWindow, project)
  }
  if (activeWindow is IdeFrame.Child) {
    return getLastFocusedSplittersForProject(activeWindow, project ?: (activeWindow as IdeFrame).project)
  }

  val frame = FocusManagerImpl.getInstance().lastFocusedFrame
  if (frame is IdeFrameImpl && frame.isActive) {
    return getLastFocusedSplittersForProject(activeWindow, frame.getProject())
  }

  // getSplitters is not implemented in unit test mode
  if (project != null && !project.isDisposed && !ApplicationManager.getApplication().isUnitTestMode) {
    // null for default project
    FileEditorManagerEx.getInstanceEx(project)?.let {
      return it.splitters
    }
  }
  return null
}

private fun getSplittersForProject(activeWindow: Window?, project: Project?): EditorsSplitters? {
  val fileEditorManager = (if (project == null || project.isDisposed) null else FileEditorManagerEx.getInstanceEx(project))
                          ?: return null
  val splitters = if (activeWindow == null) null else fileEditorManager.getSplittersFor(activeWindow)
  return splitters ?: fileEditorManager.splitters
}

private fun getLastFocusedSplittersForProject(activeWindow: Window?, project: Project?): EditorsSplitters? {
  val fileEditorManager = (if (project == null || project.isDisposed) null else FileEditorManagerEx.getInstanceEx(project))
                          ?: return null
  return (fileEditorManager as? FileEditorManagerImpl)?.getLastFocusedSplitters()
         ?: getSplittersForProject(activeWindow, project)
}

internal fun createSplitter(orientation: Boolean, proportion: Float, minProp: Float, maxProp: Float): OnePixelSplitter {
  return object : OnePixelSplitter(orientation, proportion, minProp, maxProp) {
    override fun createDivider(): Divider {
      val divider = super.createDivider()
      divider.background = JBColor.namedColor("EditorPane.splitBorder", JBColor.border())
      return divider
    }
  }
}
