// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
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
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo
import com.intellij.util.Alarm
import com.intellij.util.IconUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.containers.ArrayListSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.StartupUiUtil
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ContainerEvent
import java.awt.event.FocusEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.ref.Reference
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArraySet
import javax.swing.*

private val OPEN_FILES_ACTIVITY = Key.create<Activity>("open.files.activity")
private val LOG = logger<EditorsSplitters>()
private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"
private val OPENED_IN_BULK = Key.create<Boolean>("EditorSplitters.opened.in.bulk")

@Suppress("LeakingThis")
@DirtyUI
open class EditorsSplitters internal constructor(manager: FileEditorManagerImpl) : IdePanePanel(BorderLayout()),
                                                                                   UISettingsListener, Disposable {
  var lastFocusGainedTime = 0L
    private set

  private val myWindows = CopyOnWriteArraySet<EditorWindow>()
  val manager: FileEditorManagerImpl

  // temporarily used during initialization
  private var splittersElement: Element? = null

  @JvmField
  var myInsideChange = 0

  private val focusWatcher: MyFocusWatcher
  private val iconUpdaterAlarm = Alarm(this)

  companion object {
    const val SPLITTER_KEY: @NonNls String = "EditorsSplitters"

    @JvmStatic
    fun stopOpenFilesActivity(project: Project) {
      project.getUserData(OPEN_FILES_ACTIVITY)?.let { activity ->
        activity.end()
        project.putUserData(OPEN_FILES_ACTIVITY, null)
      }
    }

    @JvmStatic
    fun isOpenedInBulk(file: VirtualFile) = file.getUserData(OPENED_IN_BULK) != null

    @JvmStatic
    fun createSplitter(orientation: Boolean, proportion: Float, minProp: Float, maxProp: Float): OnePixelSplitter {
      return object : OnePixelSplitter(orientation, proportion, minProp, maxProp) {
        override fun createDivider(): Divider {
          val divider = super.createDivider()
          divider.background = JBColor.namedColor("EditorPane.splitBorder", JBColor.border())
          return divider
        }
      }
    }

    @JvmStatic
    fun findDefaultComponentInSplitters(project: Project?): JComponent? {
      return getSplittersToFocus(project)?.currentWindow?.selectedComposite?.preferredFocusedComponent
    }

    fun focusDefaultComponentInSplittersIfPresent(project: Project): Boolean {
      findDefaultComponentInSplitters(project)?.let {
        // not requestFocusInWindow because if floating or windowed tool window is deactivated (or, ESC pressed to focus editor),
        // then we should focus our window
        it.requestFocus()
        return true
      }
      return false
    }

    @JvmStatic
    fun activateEditorComponentOnEscape(target: Component?): Boolean {
      @Suppress("NAME_SHADOWING") var target = target
      while (target != null && target !is Window) {
        if (target is EditorsSplitters) {
          // editor is already focused
          return false
        }
        target = target.parent
      }
      if (target is FloatingDecorator) {
        target = target.getParent()
      }

      if (target !is IdeFrame) {
        return false
      }

      focusDefaultComponentInSplittersIfPresent((target as IdeFrame).project ?: return false)
      return true
    }
  }

  override fun dispose() {
    dropTarget = null
  }

  fun clear() {
    for (window in myWindows) {
      window.dispose()
    }

    removeAll()
    myWindows.clear()
    currentWindow = null
    // revalidate doesn't repaint correctly after "Close All"
    repaint()
  }

  fun startListeningFocus() {
    focusWatcher.install(this)
  }

  val currentFile: VirtualFile?
    get() = if (currentWindow != null) currentWindow!!.selectedFile else null

  private fun showEmptyText(): Boolean = currentWindow == null || currentWindow!!.files.isEmpty()

  override fun paintComponent(g: Graphics) {
    if (showEmptyText()) {
      val gg = IdeBackgroundUtil.withFrameBackground(g, this)
      super.paintComponent(gg)
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
    val composites = window.allComposites
    for (i in composites.indices) {
      val file = window.getFileAt(i)
      result.addContent(writeComposite(composites[i], window.isFilePinned(file), window.selectedComposite))
    }
  }

  private fun writeComposite(composite: EditorComposite, pinned: Boolean, selectedEditor: EditorComposite?): Element {
    val fileElement = Element("file")
    composite.currentStateAsHistoryEntry().writeExternal(fileElement, manager.project)
    fileElement.setAttribute(PINNED, java.lang.Boolean.toString(pinned))
    fileElement.setAttribute(CURRENT_IN_TAB, java.lang.Boolean.toString(composite == selectedEditor))
    return fileElement
  }

  fun restoreEditors(): JPanel? {
    val element = splittersElement ?: return null
    manager.project.putUserData(OPEN_FILES_ACTIVITY,
                                StartUpMeasurer.startActivity(StartUpMeasurer.Activities.EDITOR_RESTORING_TILL_PAINT,
                                                              ActivityCategory.DEFAULT))
    val restoringEditors = StartUpMeasurer.startActivity(StartUpMeasurer.Activities.EDITOR_RESTORING)
    val component = UIBuilder(this).process(element, topPanel)
    if (component != null) {
      component.isFocusable = false
    }
    restoringEditors.end()
    return component
  }

  fun addSelectedEditorsTo(result: MutableCollection<in FileEditor?>) {
    for (window in myWindows) {
      val composite = window.selectedComposite
      if (composite != null) {
        val editor = composite.selectedEditor
        if (!result.contains(editor)) {
          result.add(editor)
        }
      }
    }
    val currentWindow = currentWindow
    if (currentWindow != null && !myWindows.contains(currentWindow)) {
      val composite = currentWindow.selectedComposite
      if (composite != null) {
        val editor = composite.selectedEditor
        if (!result.contains(editor)) {
          result.add(editor)
        }
      }
    }
  }

  fun closeAllFiles() {
    val windows = ArrayList(myWindows)
    clear()
    for (window in windows) {
      for (file in window.files) {
        window.closeFile(file!!, false, false)
      }
    }
  }

  fun openFiles() {
    val componentRef = restoreEditors() ?: return
    ApplicationManager.getApplication().invokeAndWait({ doOpenFiles(componentRef) }, ModalityState.any())
  }

  fun doOpenFiles(component: JPanel?) {
    if (component != null) {
      removeAll()
      add(component, BorderLayout.CENTER)
      splittersElement = null
    }

    // clear empty splitters
    for (window in windows) {
      if (window.tabCount == 0) {
        window.removeFromSplitter()
      }
    }
  }

  fun readExternal(element: Element) {
    splittersElement = element
  }

  val openFileList: List<VirtualFile>
    get() {
      val files: MutableList<VirtualFile> = ArrayList()
      for (myWindow in myWindows) {
        for (composite in myWindow.allComposites) {
          val file = composite.file
          if (!files.contains(file)) {
            files.add(file)
          }
        }
      }
      return files
    }

  @get:Deprecated("Use {@link #getOpenFileList()}",
                  ReplaceWith("VfsUtilCore.toVirtualFileArray(openFileList)", "com.intellij.openapi.vfs.VfsUtilCore"))
  val openFiles: Array<VirtualFile>
    get() = VfsUtilCore.toVirtualFileArray(openFileList)

  val selectedFiles: Array<VirtualFile>
    get() {
      val files = ArrayListSet<VirtualFile>()
      for (window in myWindows) {
        window.selectedFile?.let {
          files.add(it)
        }
      }

      val virtualFiles = VfsUtilCore.toVirtualFileArray(files)
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

  fun getSelectedEditors(): Array<FileEditor> {
    val windows = HashSet<EditorWindow>(myWindows)
    currentWindow?.let {
      windows.add(it)
    }
    val editors = windows.mapNotNull { it.selectedComposite?.selectedEditor }
    return if (editors.isEmpty()) FileEditor.EMPTY_ARRAY else editors.toTypedArray()
  }

  fun updateFileIcon(file: VirtualFile) {
    updateFileIconLater(file)
  }

  fun updateFileIconImmediately(file: VirtualFile, icon: Icon) {
    val windows: Collection<EditorWindow?> = findWindows(file)
    for (window in windows) {
      window!!.updateFileIcon(file, icon)
    }
  }

  private val myFilesToUpdateIconsFor: MutableSet<VirtualFile> = HashSet()

  init {
    background = JBColor.namedColor("Editor.background", IdeBackgroundUtil.getIdeBackgroundColor())
    val l = PropertyChangeListener { e: PropertyChangeEvent ->
      val propName = e.propertyName
      if ("Editor.background" == propName || "Editor.foreground" == propName || "Editor.shortcutForeground" == propName) {
        repaint()
      }
    }
    UIManager.getDefaults().addPropertyChangeListener(l)
    Disposer.register(this) { UIManager.getDefaults().removePropertyChangeListener(l) }
    this.manager = manager
    focusWatcher = MyFocusWatcher()
    Disposer.register(this) { focusWatcher.deinstall(this) }
    focusTraversalPolicy = MyFocusTraversalPolicy()
    transferHandler = MyTransferHandler(this)
    clear()
    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        invalidate()
        repaint()
      }
    })
  }

  fun updateFileIconLater(file: VirtualFile) {
    myFilesToUpdateIconsFor.add(file)
    iconUpdaterAlarm.cancelAllRequests()
    iconUpdaterAlarm.addRequest({
                                  if (manager.project.isDisposed) return@addRequest
                                  for (file1 in myFilesToUpdateIconsFor) {
                                    updateFileIconAsynchronously(file1)
                                  }
                                  myFilesToUpdateIconsFor.clear()
                                }, 200, ModalityState.stateForComponent(this))
  }

  private fun updateFileIconAsynchronously(file: VirtualFile) {
    ReadAction
      .nonBlocking<Icon> { IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, manager.project) }
      .coalesceBy(this, "icon", file)
      .expireWith(this)
      .expireWhen { !file.isValid }
      .finishOnUiThread(ModalityState.any()) { icon: Icon -> updateFileIconImmediately(file, icon) }
      .submit(NonUrgentExecutor.getInstance())
  }

  fun updateFileColor(file: VirtualFile) {
    val windows: Collection<EditorWindow?> = findWindows(file)
    if (windows.isEmpty()) {
      return
    }
    val colorScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    for (window in windows) {
      val composite = window!!.getComposite(file)
      LOG.assertTrue(composite != null)
      val index = window.findCompositeIndex(composite!!)
      LOG.assertTrue(index != -1)
      window.setForegroundAt(index, manager.getFileColor(file))
      var attributes = if (manager.isProblem(file)) colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) else null
      if (composite.isPreview) {
        val italic = TextAttributes(null, null, null, null, Font.ITALIC)
        attributes = if (attributes == null) italic else TextAttributes.merge(italic, attributes)
      }
      window.setTextAttributes(index, attributes)
    }
  }

  fun trimToSize() {
    for (window in myWindows) {
      window.trimToSize(window.selectedFile, true)
    }
  }

  fun updateTabsLayout(newTabsLayoutInfo: TabsLayoutInfo) {
    val windows = windows
    for (i in windows.indices) {
      windows[i].updateTabsLayout(newTabsLayoutInfo)
    }
  }

  fun setTabsPlacement(tabPlacement: Int) {
    val windows = windows
    for (i in windows.indices) {
      windows[i].setTabsPlacement(tabPlacement)
    }
  }

  fun setTabLayoutPolicy(scrollTabLayout: Int) {
    val windows = windows
    for (i in windows.indices) {
      windows[i].setTabLayoutPolicy(scrollTabLayout)
    }
  }

  fun updateFileName(updatedFile: VirtualFile?) {
    for (window in windows) {
      for (file in window.files) {
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
      var ioFile: Path? = null
      try {
        ioFile = if (file is LightVirtualFileBase) null else Paths.get(file.presentableUrl)
      }
      catch (ignored: InvalidPathException) {
      }
      val finalIoFile = ioFile
      ReadAction.nonBlocking<String> { FrameTitleBuilder.getInstance().getFileTitle(project, file) }
        .expireWith(this)
        .finishOnUiThread(ModalityState.any()) { title: @NlsContexts.TabTitle String? -> frame.setFileTitle(title, finalIoFile) }
        .submit(NonUrgentExecutor.getInstance())
    }
  }

  protected open fun getFrame(project: Project): IdeFrameEx? {
    val frame = WindowManagerEx.getInstanceEx().getFrameHelper(project)
    LOG.assertTrue((ApplicationManager.getApplication().isUnitTestMode
                    || ApplicationManager.getApplication().isHeadlessEnvironment) || frame != null)
    return frame
  }

  val isInsideChange: Boolean
    get() = myInsideChange > 0

  fun updateFileBackgroundColor(file: VirtualFile) {
    val windows = windows
    for (i in windows.indices) {
      windows[i].updateFileBackgroundColor(file)
    }
  }

  val splitCount: Int
    get() {
      if (componentCount > 0) {
        val panel = getComponent(0) as JPanel
        return getSplitCount(panel)
      }
      return 0
    }

  protected open fun afterFileClosed(file: VirtualFile) {}

  protected open fun afterFileOpen(file: VirtualFile) {}

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
    get() {
      val windows = windows
      for (each in windows) {
        if (!each.isEmptyVisible) {
          return false
        }
      }
      return true
    }

  private fun findNextFile(file: VirtualFile): VirtualFile? {
    val windows = windows // TODO: use current file as base
    for (i in windows.indices) {
      val files = windows[i].files
      for (fileAt in files) {
        if (!Comparing.equal(fileAt, file)) {
          return fileAt
        }
      }
    }
    return null
  }

  fun closeFile(file: VirtualFile, moveFocus: Boolean) {
    val windows = findWindows(file)
    val isProjectOpen = manager.project.isOpen
    if (windows.isEmpty()) {
      return
    }
    val nextFile = findNextFile(file)
    for (window in windows) {
      LOG.assertTrue(window!!.selectedComposite != null)
      window.closeFile(file, false, moveFocus)
      if (window.tabCount == 0 && nextFile != null && isProjectOpen && !FileEditorManagerImpl.forbidSplitFor(nextFile)) {
        val newComposite = manager.newEditorComposite(nextFile)
        if (newComposite != null) {
          window.setComposite(newComposite, moveFocus)
        }
      }
    }
    // cleanup windows with no tabs
    for (window in windows) {
      if (!isProjectOpen || window!!.isDisposed) {
        // call to window.unsplit() which might make its sibling disposed
        continue
      }
      if (window.tabCount == 0) {
        window.unsplit(false)
      }
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    for (window in myWindows) {
      window.updateTabsVisibility(uiSettings)
    }
    if (!manager.project.isOpen) {
      return
    }
    for (file in openFileList) {
      updateFileBackgroundColor(file)
      updateFileIcon(file)
      updateFileColor(file)
    }
  }

  private inner class MyFocusTraversalPolicy : IdeFocusTraversalPolicy() {
    override fun getDefaultComponent(focusCycleRoot: Container): Component {
      if (currentWindow != null) {
        val selectedComposite = currentWindow!!.selectedComposite
        if (selectedComposite != null) {
          val focusComponent = selectedComposite.focusComponent
          if (focusComponent != null) {
            return getPreferredFocusedComponent(focusComponent, this)!!
          }
        }
      }
      return getPreferredFocusedComponent(this@EditorsSplitters, this)!!
    }

    override fun getProject(): Project {
      return manager.project
    }
  }

  val topPanel: JPanel?
    get() = if (componentCount > 0) getComponent(0) as JPanel else null

  var currentWindow: EditorWindow? = null
    private set(currentWindow) {
      require(!(currentWindow != null && !myWindows.contains(currentWindow))) { "$currentWindow is not a member of this container" }
      field = currentWindow
    }

  fun getOrCreateCurrentWindow(file: VirtualFile): EditorWindow {
    val windows = findWindows(file)
    if (currentWindow == null) {
      val iterator: Iterator<EditorWindow> = myWindows.iterator()
      if (!windows.isEmpty()) {
        setCurrentWindow(windows[0], false)
      }
      else if (iterator.hasNext()) {
        setCurrentWindow(iterator.next(), false)
      }
      else {
        createCurrentWindow()
      }
    }
    else if (!windows.isEmpty()) {
      if (!windows.contains(currentWindow)) {
        setCurrentWindow(windows[0], false)
      }
    }
    return currentWindow!!
  }

  fun createCurrentWindow() {
    LOG.assertTrue(currentWindow == null)
    currentWindow = createEditorWindow()
    add(currentWindow!!.myPanel, BorderLayout.CENTER)
  }

  internal fun createEditorWindow() = EditorWindow(this, this)

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
    if (window != null) {
      val selectedComposite = window.selectedComposite
      if (selectedComposite != null) {
        fireRunnable.run()
      }
      if (requestFocus) {
        window.requestFocus(true)
      }
    }
    else {
      fireRunnable.run()
    }
  }

  fun addWindow(window: EditorWindow) {
    myWindows.add(window)
  }

  fun removeWindow(window: EditorWindow) {
    myWindows.remove(window)
    if (currentWindow == window) {
      currentWindow = null
    }
  }

  fun containsWindow(window: EditorWindow): Boolean {
    return myWindows.contains(window)
  }

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getAllComposites()}")
  fun getEditorComposites(): List<EditorWithProviderComposite> {
    return myWindows.asSequence().flatMap { it.allComposites }.filterIsInstance(EditorWithProviderComposite::class.java).toList()
  }

  fun getAllComposites(): List<EditorComposite> = myWindows.flatMap { it.allComposites }
  //---------------------------------------------------------

  @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Use {@link #getAllComposites(VirtualFile)}")
  fun findEditorComposites(file: VirtualFile): List<EditorWithProviderComposite> {
    return ContainerUtil.filterIsInstance(getAllComposites(file), EditorWithProviderComposite::class.java)
  }

  fun getAllComposites(file: VirtualFile): List<EditorComposite?> {
    return ContainerUtil.mapNotNull(myWindows) { it: EditorWindow -> it.getComposite(file) }
  }

  private fun findWindows(file: VirtualFile): List<EditorWindow?> {
    val result: MutableList<EditorWindow?> = ArrayList(myWindows.size)
    for (window in myWindows) {
      if (window.getComposite(file) != null) {
        result.add(window)
      }
    }
    return result
  }

  val windows: Array<EditorWindow>
    get() = myWindows.toTypedArray()
  // Collector for windows in tree ordering:

  // get root component and traverse splitters tree:
  val orderedWindows: List<EditorWindow>
    get() {
      val result: MutableList<EditorWindow> = ArrayList()

      // Collector for windows in tree ordering:
      class WindowCollector {
        fun collect(panel: JPanel) {
          val comp = panel.getComponent(0)
          if (comp is Splitter) {
            collect(comp.firstComponent as JPanel)
            collect(comp.secondComponent as JPanel)
          }
          else if (comp is JPanel || comp is JBTabs) {
            val window = findWindowWith(comp)
            if (window != null) {
              result.add(window)
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
      LOG.assertTrue(result.size == myWindows.size)
      return result
    }

  internal fun findWindowWith(component: Component): EditorWindow? {
    return myWindows.firstOrNull { SwingUtilities.isDescendingFrom(component, it.myPanel) }
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
    val parent = window.myPanel.parent
    if (parent is Splitter) {
      val component = parent.secondComponent
      if (component !== window.myPanel) {
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

private class UIBuilder(private val splitters: EditorsSplitters) : ConfigTreeReader<JPanel?>() {
  override fun processFiles(fileElements: List<Element>, tabSizeLimit: Int, context: JPanel?): JPanel? {
    var window: EditorWindow? = null
    ApplicationManager.getApplication().invokeAndWait {
      val editorWindow = context?.let { splitters.findWindowWith(it) } ?: splitters.createEditorWindow()
      window = editorWindow
      splitters.setCurrentWindow(window = editorWindow, requestFocus = false)
      if (tabSizeLimit != 1) {
        editorWindow.tabbedPane.component.putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, tabSizeLimit)
      }
    }

    LOG.assertTrue(window != null)
    var focusedFile: VirtualFile? = null
    val fileEditorManager = splitters.manager
    for (i in fileElements.indices) {
      val file = fileElements[i]
      val historyElement = file.getChild(HistoryEntry.TAG)
      val fileName = historyElement.getAttributeValue(HistoryEntry.FILE_ATTR)
      val activity = StartUpMeasurer.startActivity(PathUtil.getFileName(fileName), ActivityCategory.REOPENING_EDITOR)
      val entry = HistoryEntry.createLight(fileEditorManager.project, historyElement)
      val virtualFile = entry.file
      if (virtualFile == null) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          LOG.error(InvalidDataException("No file exists: ${entry.filePointer.url}"))
        }
      }
      else {
        val openOptions = FileEditorOpenOptions(
          selectAsCurrent = false,
          pin = file.getAttributeValue(PINNED).toBoolean(),
          index = i,
          isReopeningOnStartup = true,
        )
        try {
          virtualFile.putUserData(OPENED_IN_BULK, true)
          val document = ReadAction.compute<Document?, RuntimeException> {
            if (virtualFile.isValid) FileDocumentManager.getInstance().getDocument(virtualFile) else null
          }
          val isCurrentTab = file.getAttributeValue(CURRENT_IN_TAB).toBoolean()
          fileEditorManager.openFileImpl4(window!!, virtualFile, entry, openOptions)
          // This is just to make sure document reference is kept on stack till this point
          // so that document is available for folding state deserialization in HistoryEntry constructor
          // and that document will be created only once during file opening
          Reference.reachabilityFence(document)
          if (isCurrentTab) {
            focusedFile = virtualFile
          }
        }
        catch (e: InvalidDataException) {
          if (ApplicationManager.getApplication().isUnitTestMode) {
            LOG.error(e)
          }
        }
        finally {
          virtualFile.putUserData(OPENED_IN_BULK, null)
        }
      }
      activity.end()
    }

    if (focusedFile == null) {
      val manager = getInstance(splitters.manager.project)
      manager.invokeLater {
        if (manager.activeToolWindowId == null) {
          manager.getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
        }
      }
    }
    else {
      fileEditorManager.addSelectionRecord(focusedFile, window!!)
      EdtInvocationManager.invokeLaterIfNeeded {
        window!!.getComposite(focusedFile)?.let {
          window!!.setComposite(it, true)
        }
      }
    }
    return window!!.myPanel
  }

  override fun processSplitter(element: Element,
                               firstChild: Element?,
                               secondChild: Element?,
                               context: JPanel?): JPanel? {
    if (context == null) {
      val orientation = "vertical" == element.getAttributeValue("split-orientation")
      val proportion = element.getAttributeValue("split-proportion").toFloat()
      val firstComponent = process(firstChild!!, null)!!
      val secondComponent = process(secondChild!!, null)!!
      var panel: JPanel? = null
      ApplicationManager.getApplication().invokeAndWait {
        panel = JPanel(BorderLayout())
        panel!!.isOpaque = false
        val splitter = EditorsSplitters.createSplitter(orientation = orientation, proportion = proportion, minProp = 0.1f, maxProp = 0.9f)
        splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)
        panel!!.add(splitter, BorderLayout.CENTER)
        splitter.firstComponent = firstComponent
        splitter.secondComponent = secondComponent
      }
      return panel
    }

    var firstComponent: JPanel? = null
    var secondComponent: JPanel? = null
    ApplicationManager.getApplication().invokeAndWait {
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
    process(element = firstChild!!, context = firstComponent)
    process(element = secondChild!!, context = secondComponent)
    return context
  }
}

private abstract class ConfigTreeReader<T> {
  fun process(element: Element, context: T?): T? {
    element.getChild("splitter")?.let { splitterElement ->
      val first = splitterElement.getChild("split-first")
      val second = splitterElement.getChild("split-second")
      return processSplitter(splitterElement, first, second, context)
    }

    val leaf = element.getChild("leaf") ?: return null
    val fileElements = leaf.getChildren("file")
    val children: List<Element>
    if (fileElements.isEmpty()) {
      children = emptyList()
    }
    else {
      children = ArrayList(fileElements.size)
      // trim to EDITOR_TAB_LIMIT, ignoring CLOSE_NON_MODIFIED_FILES_FIRST policy
      var toRemove = fileElements.size - EditorWindow.getTabLimit()
      for (fileElement in fileElements) {
        if (toRemove <= 0 || java.lang.Boolean.parseBoolean(fileElement.getAttributeValue(PINNED))) {
          children.add(fileElement)
        }
        else {
          toRemove--
        }
      }
    }
    return processFiles(fileElements = children,
                        tabSizeLimit = StringUtil.parseInt(leaf.getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString()), -1),
                        context = context)
  }

  abstract fun processFiles(fileElements: List<Element>, tabSizeLimit: Int, context: T?): T?

  abstract fun processSplitter(element: Element, firstChild: Element?, secondChild: Element?, context: T?): T?
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

private fun getSplittersToFocus(project: Project?): EditorsSplitters? {
  @Suppress("NAME_SHADOWING") var project = project
  var activeWindow = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
  if (activeWindow is FloatingDecorator) {
    val lastFocusedFrame = IdeFocusManager.findInstanceByComponent(activeWindow).lastFocusedFrame
    val frameComponent = lastFocusedFrame?.component
    val lastFocusedWindow = if (frameComponent == null) null else SwingUtilities.getWindowAncestor(frameComponent)
    activeWindow = ObjectUtils.notNull(lastFocusedWindow, activeWindow)
    if (project == null) {
      project = lastFocusedFrame?.project
    }
    val fileEditorManager = (if (project == null || project.isDisposed) null else FileEditorManagerEx.getInstanceEx(project))
                            ?: return null
    return fileEditorManager.getSplittersFor(activeWindow) ?: fileEditorManager.splitters
  }
  if (activeWindow is IdeFrame.Child) {
    if (project == null) {
      project = (activeWindow as IdeFrame.Child).project
    }
    return getSplittersForProject(WindowManager.getInstance().getFrame(project), project)
  }
  val frame = FocusManagerImpl.getInstance().lastFocusedFrame
  if (frame is IdeFrameImpl && frame.isActive) {
    return getSplittersForProject(activeWindow, frame.getProject())
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