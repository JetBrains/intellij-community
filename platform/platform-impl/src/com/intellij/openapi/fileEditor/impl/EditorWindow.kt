// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "PrivatePropertyName")

package com.intellij.openapi.fileEditor.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToggleDistractionFreeModeAction
import com.intellij.ide.ui.UISettings
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorsSplitters.Companion.createSplitter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale.scaleIcon
import com.intellij.ui.tabs.TabsUtil
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.IconUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.Stack
import com.intellij.util.ui.*
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.function.BiFunction
import java.util.function.Function
import javax.swing.*
import kotlin.math.roundToInt

private val LOG = logger<EditorWindow>()

class EditorWindow internal constructor(val owner: EditorsSplitters, parentDisposable: Disposable) {
  companion object {
    @JvmField
    val DATA_KEY = DataKey.create<EditorWindow>("editorWindow")

    @JvmField
    val HIDE_TABS = Key.create<Boolean>("HIDE_TABS")

    // Metadata to support editor tab drag&drop process: initial index
    internal val DRAG_START_INDEX_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor index", -1)

    // Metadata to support editor tab drag&drop process: hash of source container
    internal val DRAG_START_LOCATION_HASH_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor location hash", 0)

    // Metadata to support editor tab drag&drop process: initial 'pinned' state
    internal val DRAG_START_PINNED_KEY = Key.create<Boolean>("drag start editor pinned state")

    @JvmStatic
    val tabLimit: Int
      get() {
        if (ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled()
            && ToggleDistractionFreeModeAction.getStandardTabPlacement() == UISettings.TABS_NONE) {
          return 1
        }
        else {
          return UISettings.getInstance().editorTabLimit
        }
      }
  }

  @JvmField
  internal var panel: JPanel

  val tabbedPane: EditorTabbedContainer

  var isDisposed = false
    private set

  private val removedTabs: Stack<Pair<String, FileEditorOpenOptions>> = object : Stack<Pair<String, FileEditorOpenOptions>>() {
    override fun push(pair: Pair<String, FileEditorOpenOptions>) {
      if (size >= tabLimit) {
        removeAt(0)
      }
      super.push(pair)
    }
  }

  val isShowing: Boolean
    get() = panel.isShowing

  val manager: FileEditorManagerImpl
    get() = owner.manager
  val tabCount: Int
    get() = tabbedPane.tabCount

  fun setForegroundAt(index: Int, color: Color) {
    tabbedPane.setForegroundAt(index, color)
  }

  fun setTextAttributes(index: Int, attributes: TextAttributes?) {
    tabbedPane.setTextAttributes(index, attributes)
  }

  private fun setTitleAt(index: Int, text: @NlsContexts.TabTitle String) {
    tabbedPane.setTitleAt(index, text)
  }

  private fun setBackgroundColorAt(index: Int, color: Color?) {
    tabbedPane.setBackgroundColorAt(index, color)
  }

  private fun setToolTipTextAt(index: Int, text: @NlsContexts.Tooltip String?) {
    tabbedPane.setToolTipTextAt(index, text)
  }

  fun setTabLayoutPolicy(policy: Int) {
    tabbedPane.setTabLayoutPolicy(policy)
  }

  fun setTabsPlacement(tabPlacement: Int) {
    tabbedPane.setTabPlacement(tabPlacement)
  }

  fun setAsCurrentWindow(requestFocus: Boolean) {
    owner.setCurrentWindow(this, requestFocus)
  }

  val isEmptyVisible: Boolean
    get() = tabbedPane.isEmptyVisible
  val size: Dimension
    get() = panel.size

  private fun checkConsistency() {
    LOG.assertTrue(isValid, "EditorWindow not in collection")
  }

  val isValid: Boolean
    get() = owner.containsWindow(this)

  @Suppress("DEPRECATION")
  @get:Deprecated("Use {@link #getSelectedComposite}", ReplaceWith("getSelectedComposite(false)"))
  val selectedEditor: EditorWithProviderComposite?
    get() = getSelectedComposite(false) as EditorWithProviderComposite?
  val selectedComposite: EditorComposite?
    get() = getSelectedComposite(false)

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getSelectedComposite}", ReplaceWith("getSelectedComposite(ignorePopup)"))
  fun getSelectedEditor(ignorePopup: Boolean): EditorWithProviderComposite? {
    return getSelectedComposite(ignorePopup) as EditorWithProviderComposite?
  }

  /**
   * @param ignorePopup if `false` and context menu is shown currently for some tab,
   * composite for which menu is invoked will be returned
   */
  fun getSelectedComposite(ignorePopup: Boolean): EditorComposite? {
    return (tabbedPane.getSelectedComponent(ignorePopup) as? EditorWindowTopComponent)?.composite
  }

  val allComposites: List<EditorComposite>
    get() = composites.toList()

  val composites: Sequence<EditorComposite>
    get() = IntRange(0, tabCount - 1).asSequence().map(::getCompositeAt)

  @Suppress("DEPRECATION")
  @get:Deprecated("{@link #getAllComposites()}", ReplaceWith("allComposites)"))
  val editors: Array<EditorWithProviderComposite>
    get() = composites.filterIsInstance<EditorWithProviderComposite>().toList().toTypedArray()

  val files: Array<VirtualFile>
    get() = composites.map { it.file }.toList().toTypedArray()

  val fileList: List<VirtualFile>
    get() = composites.map { it.file }.toList()

  val fileSequence: Sequence<VirtualFile>
    get() = composites.map { it.file }

  private var painter: MySplitPainter? = null

  init {
    panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.isFocusable = false
    tabbedPane = EditorTabbedContainer(this, manager.project, parentDisposable)
    panel.add(tabbedPane.component, BorderLayout.CENTER)

    // tab layout policy
    if (UISettings.getInstance().scrollTabLayoutInEditor) {
      setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
    }
    else {
      setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT)
    }
    owner.addWindow(this)
    updateTabsVisibility()
  }

  enum class RelativePosition(val mySwingConstant: Int) {
    CENTER(SwingConstants.CENTER),
    UP(SwingConstants.TOP),
    LEFT(SwingConstants.LEFT),
    DOWN(SwingConstants.BOTTOM),
    RIGHT(SwingConstants.RIGHT)
  }

  private fun getAdjacentEditors(): Map<RelativePosition, EditorWindow> {
    checkConsistency()
    val adjacentEditors = HashMap<RelativePosition, EditorWindow>(4) // can't have more than 4
    val windows = owner.getOrderedWindows()
    windows.remove(this)
    val panelToWindow = HashMap<JPanel, EditorWindow>()
    for (window in windows) {
      panelToWindow.put(window.panel, window)
    }
    val relativePoint = RelativePoint(panel.locationOnScreen)
    val point = relativePoint.getPoint(owner)
    val nearestComponent = BiFunction { x: Int, y: Int ->
      SwingUtilities.getDeepestComponentAt(owner, x, y)
    }
    val findAdjacentEditor = Function<Component, EditorWindow?> { startComponent ->
      var component = startComponent
      while (component !== owner) {
        if (panelToWindow.containsKey(component)) {
          return@Function panelToWindow.get(component)
        }
        component = component.parent ?: break
      }
      null
    }
    val biConsumer: (EditorWindow?, RelativePosition) -> Unit = { window, position ->
      if (window != null) {
        adjacentEditors.put(position, window)
      }
    }

    // Even if above/below adjacent editor is shifted a bit to the right from left edge of current editor,
    // still try to choose editor that is visually above/below - shifted nor more then quater of editor width.
    val x = point.x + panel.width / 4
    // Splitter has width of one pixel - we need to step at least 2 pixels to be over adjacent editor
    val searchStep = 2
    biConsumer(findAdjacentEditor.apply(nearestComponent.apply(x, point.y - searchStep)), RelativePosition.UP)
    biConsumer(findAdjacentEditor.apply(nearestComponent.apply(x, point.y + panel.height + searchStep)), RelativePosition.DOWN)
    biConsumer(findAdjacentEditor.apply(nearestComponent.apply(point.x - searchStep, point.y)), RelativePosition.LEFT)
    biConsumer(findAdjacentEditor.apply(nearestComponent.apply(point.x + panel.width + searchStep, point.y)), RelativePosition.RIGHT)
    return adjacentEditors
  }

  @Deprecated("{@link #setSelectedComposite(EditorComposite, boolean)}", ReplaceWith("setSelectedComposite(composite, focusEditor)"))
  fun setSelectedEditor(composite: EditorComposite, focusEditor: Boolean) {
    setSelectedComposite(composite, focusEditor)
  }

  fun setSelectedComposite(composite: EditorComposite, focusEditor: Boolean) {
    // select a composite in a tabbed pane and then focus a composite if needed
    val index = findFileIndex(composite.file)
    if (index != -1 && !isDisposed) {
      tabbedPane.setSelectedIndex(index, focusEditor)
    }
  }

  @Deprecated("Use {@link #setComposite(EditorComposite, boolean)}",
              ReplaceWith("setComposite(editor, FileEditorOpenOptions().withRequestFocus(focusEditor))",
                          "com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions"))
  fun setEditor(@Suppress("DEPRECATION") editor: EditorWithProviderComposite, focusEditor: Boolean) {
    setComposite(editor, FileEditorOpenOptions(requestFocus = focusEditor))
  }

  internal fun setComposite(composite: EditorComposite, focusEditor: Boolean) {
    setComposite(composite, FileEditorOpenOptions(requestFocus = focusEditor, usePreviewTab = composite.isPreview))
  }

  internal fun setComposite(composite: EditorComposite, options: FileEditorOpenOptions) {
    val isNewEditor = findCompositeIndex(composite) == -1
    val isPreviewMode = (isNewEditor || composite.isPreview) && shouldReservePreview(composite.file, options, owner.manager.project)
    val wasPinned = composite.isPinned
    composite.isPreview = isPreviewMode
    if (isNewEditor) {
      var indexToInsert = options.index
      if (indexToInsert == -1 && isPreviewMode) {
        indexToInsert = findPreviewIndex()
      }
      if (indexToInsert == -1) {
        indexToInsert = if (UISettings.getInstance().openTabsAtTheEnd) tabbedPane.tabCount else tabbedPane.selectedIndex + 1
      }
      val file = composite.file
      val template = AllIcons.FileTypes.Text
      val emptyIcon = EmptyIcon.create(template.iconWidth, template.iconHeight)
      tabbedPane.insertTab(file, emptyIcon, EditorWindowTopComponent(this, composite), null, indexToInsert, composite, composite)
      var dragStartIndex: Int? = null
      val hash = file.getUserData(DRAG_START_LOCATION_HASH_KEY)
      if (hash != null && System.identityHashCode(tabbedPane.tabs) == hash) {
        dragStartIndex = file.getUserData(DRAG_START_INDEX_KEY)
      }
      if (dragStartIndex == null || dragStartIndex != -1) {
        val initialPinned = file.getUserData(DRAG_START_PINNED_KEY)
        if (initialPinned != null) {
          composite.isPinned = initialPinned
        }
        else if (wasPinned) {
          composite.isPinned = true
        }
      }
      file.putUserData(DRAG_START_LOCATION_HASH_KEY, null)
      file.putUserData(DRAG_START_INDEX_KEY, null)
      file.putUserData(DRAG_START_PINNED_KEY, null)
      trimToSize(fileToIgnore = file, transferFocus = false)
      owner.updateFileIconImmediately(file = file, icon = IconUtil.computeBaseFileIcon(file))
      owner.updateFileIconLater(file)
      owner.updateFileColor(file)
    }
    owner.updateFileColor(composite.file)
    if (options.selectAsCurrent) {
      setSelectedComposite(composite, options.requestFocus)
    }
    updateTabsVisibility()
    owner.validate()
  }

  private fun splitAvailable(): Boolean = tabCount >= 1

  @JvmOverloads
  fun split(orientation: Int,
            forceSplit: Boolean,
            virtualFile: VirtualFile?,
            focusNew: Boolean,
            fileIsSecondaryComponent: Boolean = true): EditorWindow? {
    checkConsistency()
    if (!splitAvailable()) {
      return null
    }

    val fileEditorManager = owner.manager
    if (!forceSplit && inSplitter()) {
      val siblings = getSiblings()
      val target = siblings[0]
      if (virtualFile != null) {
        syncCaretIfPossible(fileEditorManager.openFileImpl4(window = target,
                                                            _file = virtualFile,
                                                            entry = null,
                                                            options = FileEditorOpenOptions(requestFocus = focusNew)).allEditors)
      }
      return target
    }

    val panel = panel
    panel.border = null

    if (tabCount == 0) {
      return null
    }

    this.panel = JPanel(BorderLayout())
    this.panel.isOpaque = false
    val splitter = createSplitter(orientation == JSplitPane.VERTICAL_SPLIT, 0.5f, 0.1f, 0.9f)
    splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, java.lang.Boolean.TRUE)
    val result = EditorWindow(owner = owner, parentDisposable = owner)
    val selectedComposite = selectedComposite
    panel.remove(tabbedPane.component)
    panel.add(splitter, BorderLayout.CENTER)
    if (fileIsSecondaryComponent) {
      splitter.firstComponent = this.panel
    }
    else {
      splitter.secondComponent = this.panel
    }
    this.panel.add(tabbedPane.component, BorderLayout.CENTER)
    if (fileIsSecondaryComponent) {
      splitter.secondComponent = result.panel
    }
    else {
      splitter.firstComponent = result.panel
    }
    normalizeProportionsIfNeed(this.panel)
    // open only selected file in the new splitter instead of opening all tabs
    val nextFile = virtualFile ?: selectedComposite!!.file
    val currentState = selectedComposite?.currentStateAsHistoryEntry()?.takeIf { it.file != nextFile }
    val openOptions = FileEditorOpenOptions(requestFocus = focusNew, isExactState = true)
    val editors = fileEditorManager.openFileImpl4(result, nextFile, currentState, openOptions).allEditors
    syncCaretIfPossible(editors)
    if (isFileOpen(nextFile)) {
      result.setFilePinned(nextFile, isFilePinned(nextFile))
    }
    if (!focusNew) {
      result.setSelectedComposite(composite = selectedComposite!!, focusEditor = true)
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        selectedComposite.focusComponent?.let {
          IdeFocusManager.getGlobalInstance().requestFocus(it, true)
        }
      }
    }
    panel.revalidate()
    return result
  }

  private fun normalizeProportionsIfNeed(inputComponent: Container) {
    var component = inputComponent
    if (!getBoolean("editor.normalize.splits")) {
      return
    }

    var isVertical = when {
      component is Splitter -> component.isVertical
      component.components.firstOrNull() is Splitter -> (component.components.first() as Splitter).isVertical
      else -> null
    }

    val hierarchyStack = LinkedHashMap<Splitter, Boolean>()
    while (component !== manager.mainSplitters) {
      val parent = component.parent
      if (parent is Splitter) {
        if (isVertical === null) {
          // stack orientation (row or column) is not yet defined
          isVertical = parent.isVertical
        }
        else if (isVertical != parent.isVertical) {
          break
        }
        hierarchyStack.put(parent, parent.firstComponent === component)
      }
      component = parent ?: break
    }

    var i = 0
    for ((key, value) in hierarchyStack) {
      key.proportion = if (value) 1 - 1f / (2 + i) else 1f / (2 + i)
      i++
    }
  }

  /**
   * Tries to set up caret and viewport for the given editor from the selected one.
   */
  private fun syncCaretIfPossible(toSync: List<FileEditor>) {
    val from = selectedComposite ?: return
    val caretSource = from.selectedEditor as? TextEditor ?: return
    val editorFrom = caretSource.editor
    val offset = editorFrom.caretModel.offset
    if (offset <= 0) {
      return
    }

    val scrollOffset = editorFrom.scrollingModel.verticalScrollOffset
    for (fileEditor in toSync) {
      if (fileEditor !is TextEditor) {
        continue
      }

      val editor = fileEditor.editor
      if (editorFrom.document === editor.document) {
        editor.caretModel.moveToOffset(offset)
        val scrollingModel = editor.scrollingModel
        scrollingModel.scrollVertically(scrollOffset)
        SwingUtilities.invokeLater {
          if (!editor.isDisposed) {
            scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
          }
        }
      }
    }
  }

  @Deprecated("Use getSiblings()", ReplaceWith("getSiblings()"))
  fun findSiblings(): Array<EditorWindow> = getSiblings().toTypedArray()

  fun getSiblings(): List<EditorWindow> {
    checkConsistency()
    val splitter = (panel.parent as? Splitter) ?: return emptyList()
    return owner.getWindows().filter { win -> win != this@EditorWindow && SwingUtilities.isDescendingFrom(win.panel, splitter) }
  }

  @RequiresEdt
  fun updateFileBackgroundColor(file: VirtualFile, backgroundColor: Color?) {
    val index = findFileEditorIndex(file)
    if (index != -1) {
      setBackgroundColorAt(index, backgroundColor)
    }
  }

  fun requestFocus(forced: Boolean) {
    tabbedPane.requestFocus(forced)
  }

  fun toFront() {
    UIUtil.toFront(ComponentUtil.getWindow(tabbedPane.component))
  }

  fun updateTabsVisibility(settings: UISettings = UISettings.getInstance()) {
    tabbedPane.tabs.presentation.isHideTabs = (owner.isFloating && this.tabCount == 1 && shouldHideTabs(selectedComposite)) ||
                                              settings.editorTabPlacement == UISettings.TABS_NONE || settings.presentationMode
  }

  fun closeAllExcept(selectedFile: VirtualFile?) {
    FileEditorManagerImpl.runBulkTabChange(owner) {
      for (file in files) {
        if (file != selectedFile && !isFilePinned(file)) {
          closeFile(file)
        }
      }
    }
  }

  fun dispose() {
    try {
      owner.removeWindow(this)
    }
    finally {
      isDisposed = true
    }
  }

  fun hasClosedTabs(): Boolean = !removedTabs.empty()

  fun restoreClosedTab() {
    assert(hasClosedTabs()) { "Nothing to restore" }
    val info = removedTabs.pop()
    val file = VirtualFileManager.getInstance().findFileByUrl(info.getFirst())
    if (file != null) {
      manager.openFileImpl4(this, file, null, info.getSecond().copy(selectAsCurrent = true, requestFocus = true))
    }
  }

  @JvmOverloads
  fun closeFile(file: VirtualFile, disposeIfNeeded: Boolean = true, transferFocus: Boolean = true) {
    val editorManager = manager
    FileEditorManagerImpl.runBulkTabChange(owner) {
      val composites = owner.getAllComposites(file)
      if (!isDisposed && composites.isEmpty()) {
        return@runBulkTabChange
      }

      try {
        val composite = getComposite(file)
        val beforePublisher = editorManager.project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)
        beforePublisher.beforeFileClosed(editorManager, file)
        if (composite != null) {
          val componentIndex = findComponentIndex(composite.component)
          // composite could close itself on decomposition
          if (componentIndex >= 0) {
            val indexToSelect = calcIndexToSelect(file, componentIndex)
            val options = FileEditorOpenOptions().withIndex(componentIndex).withPin(composite.isPinned)
            val pair = Pair(file.url, options)
            removedTabs.push(pair)
            tabbedPane.removeTabAt(componentIndex, indexToSelect, transferFocus)
            editorManager.disposeComposite(composite)
          }
        }
        else {
          if (inSplitter()) {
            val splitter = panel.parent as Splitter
            splitter.getOtherComponent(panel)?.let { otherComponent ->
              IdeFocusManager.findInstance().requestFocus(otherComponent, true)
            }
          }
          panel.removeAll()
        }
        if (disposeIfNeeded && tabCount == 0) {
          removeFromSplitter()
        }
        else {
          panel.revalidate()
        }
      }
      finally {
        editorManager.removeSelectionRecord(file, this)
        (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
        editorManager.notifyPublisher {
          val project = editorManager.project
          if (!project.isDisposed) {
            project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(editorManager, file)
          }
        }
        owner.afterFileClosed(file)
      }
    }
  }

  fun removeFromSplitter() {
    if (!inSplitter()) {
      return
    }

    if (owner.currentWindow == this) {
      val siblings = getSiblings()
      owner.setCurrentWindow(window = siblings.first(), requestFocus = true)
    }

    val splitter = panel.parent as Splitter
    val otherComponent = splitter.getOtherComponent(panel)
    when (val parent = splitter.parent.parent) {
      is Splitter -> {
        if (parent.firstComponent === splitter.parent) {
          parent.firstComponent = otherComponent
        }
        else {
          parent.secondComponent = otherComponent
        }
        normalizeProportionsIfNeed(owner.currentWindow!!.panel)
      }
      is EditorsSplitters -> {
        val currentFocusComponent = IdeFocusManager.getGlobalInstance().getFocusedDescendantFor(parent)
        parent.removeAll()
        parent.add(otherComponent, BorderLayout.CENTER)
        parent.revalidate()
        currentFocusComponent?.requestFocusInWindow()
      }
      else -> throw IllegalStateException("Unknown container: $parent")
    }
    dispose()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  internal fun calcIndexToSelect(fileBeingClosed: VirtualFile, fileIndex: Int): Int {
    val currentlySelectedIndex = tabbedPane.selectedIndex
    if (currentlySelectedIndex != fileIndex) {
      // if the file being closed is not currently selected, keep the currently selected file open
      return currentlySelectedIndex
    }
    val uiSettings = UISettings.getInstance()
    if (uiSettings.state.activeMruEditorOnClose) {
      // try to open last visited file
      val histFiles = EditorHistoryManager.getInstance(manager.project).fileList
      for (index in histFiles.indices.reversed()) {
        val histFile = histFiles.get(index)
        if (histFile == fileBeingClosed) {
          continue
        }

        val composite = getComposite(histFile) ?: continue  // ????
        val histFileIndex = findComponentIndex(composite.component)
        if (histFileIndex >= 0) {
          // if the file being closed is located before the hist file, then after closing the index of the histFile will be shifted by -1
          return histFileIndex
        }
      }
    }
    else if (uiSettings.activeRightEditorOnClose && fileIndex + 1 < tabbedPane.tabCount) {
      return fileIndex + 1
    }

    // by default select previous neighbour
    return if (fileIndex > 0) fileIndex - 1 else -1
  }

  private fun showSplitChooser(showInfoPanel: Boolean): () -> Unit {
    painter = MySplitPainter(showInfoPanel)
    val disposable = Disposer.newDisposable("GlassPaneListeners")
    IdeGlassPaneUtil.find(panel).addPainter(panel, painter!!, disposable)
    panel.repaint()
    panel.isFocusable = true
    panel.grabFocus()
    panel.focusTraversalKeysEnabled = false
    val focusAdapter = object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        panel.removeFocusListener(this)
        val splitterService = SplitterService.getInstance()
        if (splitterService.activeWindow == this@EditorWindow) {
          splitterService.stopSplitChooser(true)
        }
      }
    }
    panel.addFocusListener(focusAdapter)
    return {
      painter!!.rectangle = null
      painter = null
      panel.removeFocusListener(focusAdapter)
      panel.isFocusable = false
      panel.repaint()
      Disposer.dispose(disposable)
    }
  }

  private inner class MySplitPainter(private var showInfoPanel: Boolean) : AbstractPainter() {
    var rectangle: Shape? = tabbedPane.tabs.dropArea
    var position = RelativePosition.CENTER

    override fun needsRepaint(): Boolean = rectangle != null

    override fun executePaint(component: Component, g: Graphics2D) {
      if (rectangle == null) {
        return
      }

      GraphicsUtil.setupAAPainting(g)
      g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
      g.fill(rectangle)
      if (position == RelativePosition.CENTER && showInfoPanel) {
        drawInfoPanel(component, g)
      }
    }

    private fun drawInfoPanel(component: Component, g: Graphics2D) {
      val rectangle = rectangle!!
      val centerX = rectangle.bounds.x + rectangle.bounds.width / 2
      val centerY = rectangle.bounds.y + rectangle.bounds.height / 2
      val height = Registry.intValue("ide.splitter.chooser.info.panel.height")
      var width = Registry.intValue("ide.splitter.chooser.info.panel.width")
      val arc = Registry.intValue("ide.splitter.chooser.info.panel.arc")
      val getShortcut: (actionId: String) -> String = { actionId ->
        val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)
        KeymapUtil.getKeystrokeText(shortcut?.firstKeyStroke)
      }
      val openShortcuts = String.format(IdeBundle.message("split.with.chooser.move.tab"), getShortcut("SplitChooser.Split"),
                                        if (SplitterService.getInstance().initialEditorWindow != null) String.format(
                                          IdeBundle.message("split.with.chooser.duplicate.tab"),
                                          getShortcut("SplitChooser.Duplicate"))
                                        else "")
      val switchShortcuts = String.format(IdeBundle.message("split.with.chooser.switch.tab"), getShortcut("SplitChooser.NextWindow"))

      // Adjust default width to info text
      val font = StartupUiUtil.getLabelFont()
      val fontMetrics = g.getFontMetrics(font)
      val openShortcutsWidth = fontMetrics.stringWidth(openShortcuts)
      val switchShortcutsWidth = fontMetrics.stringWidth(switchShortcuts)
      width = width.coerceAtLeast((openShortcutsWidth.coerceAtLeast(switchShortcutsWidth) * 1.2f).roundToInt())

      // Check if info panel will actually fit into editor with some free space around edges
      if (rectangle.bounds.height < height * 1.2f || rectangle.bounds.width < width * 1.2f) {
        return
      }

      val shape = RoundRectangle2D.Double(centerX - width / 2.0, centerY - height / 2.0, width.toDouble(), height.toDouble(),
                                          arc.toDouble(), arc.toDouble())
      g.color = UIUtil.getLabelBackground()
      g.fill(shape)
      val arrowsCenterVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.center")
      val arrowsVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.vertical")
      val arrowsHShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.horizontal")
      val function = Function { icon: Icon -> Point(centerX - icon.iconWidth / 2, centerY - icon.iconHeight / 2 + arrowsCenterVShift) }
      val forUpDownIcons = function.apply(AllIcons.Chooser.Top)
      AllIcons.Chooser.Top.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y - arrowsVShift)
      AllIcons.Chooser.Bottom.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y + arrowsVShift)
      val forLeftRightIcons = function.apply(AllIcons.Chooser.Right)
      AllIcons.Chooser.Right.paintIcon(component, g, forLeftRightIcons.x + arrowsHShift, forLeftRightIcons.y)
      AllIcons.Chooser.Left.paintIcon(component, g, forLeftRightIcons.x - arrowsHShift, forLeftRightIcons.y)
      val textVShift = Registry.intValue("ide.splitter.chooser.info.panel.text.shift")
      val textY = forUpDownIcons.y + AllIcons.Chooser.Bottom.iconHeight + textVShift
      g.color = NamedColorUtil.getInactiveTextColor()
      g.font = font
      g.drawString(openShortcuts, centerX - openShortcutsWidth / 2, textY)
      if (owner.getWindows().size > 1) {
        g.drawString(switchShortcuts, centerX - switchShortcutsWidth / 2, textY + fontMetrics.height)
      }
    }

    fun positionChanged(position: RelativePosition) {
      if (this.position == position) {
        return
      }

      showInfoPanel = false
      this.position = position
      rectangle = null
      setNeedsRepaint(true)
      val r = tabbedPane.tabs.dropArea
      TabsUtil.updateBoundsWithDropSide(r, this.position.mySwingConstant)
      rectangle = Rectangle2D.Double(r.x.toDouble(), r.y.toDouble(), r.width.toDouble(), r.height.toDouble())
    }
  }

  @Service(Service.Level.APP)
  class SplitterService {
    companion object {
      @JvmStatic
      fun getInstance(): SplitterService = service()
    }

    var activeWindow: EditorWindow? = null
    private var virtualFile: VirtualFile? = null
    private var splitChooserDisposer: (() -> Unit)? = null
    var initialEditorWindow: EditorWindow? = null

    fun activateSplitChooser(window: EditorWindow, file: VirtualFile, openedFromEditor: Boolean) {
      if (isActive) {
        stopSplitChooser(true)
      }
      activeWindow = window
      virtualFile = file
      if (openedFromEditor) {
        initialEditorWindow = activeWindow
      }
      splitChooserDisposer = activeWindow!!.showSplitChooser(true)
    }

    private fun switchWindow(window: EditorWindow) {
      splitChooserDisposer?.invoke()
      activeWindow = window
      splitChooserDisposer = window.showSplitChooser(false)
    }

    fun stopSplitChooser(interrupted: Boolean) {
      val activeWindow = activeWindow
      this.activeWindow = null
      virtualFile = null
      splitChooserDisposer?.invoke()
      splitChooserDisposer = null
      initialEditorWindow = null
      if (!interrupted) {
        activeWindow!!.requestFocus(true)
      }
    }

    val isActive: Boolean
      get() = activeWindow != null

    fun nextWindow() {
      if (!isActive) {
        return
      }

      val orderedWindows = activeWindow!!.owner.getOrderedWindows()
      val index = (orderedWindows.indexOf(activeWindow) + 1) % orderedWindows.size
      switchWindow(orderedWindows.get(index))
    }

    fun previousWindow() {
      if (!isActive) {
        return
      }

      val orderedWindows = activeWindow!!.owner.getOrderedWindows()
      var index = orderedWindows.indexOf(activeWindow) - 1
      index = if (index < 0) orderedWindows.size - 1 else index
      switchWindow(orderedWindows.get(index))
    }

    fun split(move: Boolean) {
      val activeWindow = activeWindow
      val initialWindow = initialEditorWindow
      val file = virtualFile
      val position = activeWindow?.painter?.position
      stopSplitChooser(false)

      // if a position is default and focus is still in the same editor window => nothing need to be done
      if (position == RelativePosition.CENTER && initialWindow == activeWindow) {
        return
      }

      if (position == RelativePosition.CENTER) {
        activeWindow.manager.openFile(file!!, true)
      }
      else {
        activeWindow!!.split(
          orientation = if (position == RelativePosition.UP || position == RelativePosition.DOWN) {
            JSplitPane.VERTICAL_SPLIT
          }
          else {
            JSplitPane.HORIZONTAL_SPLIT
          },
          forceSplit = true,
          virtualFile = file,
          focusNew = true,
          fileIsSecondaryComponent = position != RelativePosition.LEFT && position != RelativePosition.UP
        )
      }
      if (initialWindow != null && move) {
        initialWindow.closeFile(file = file!!, disposeIfNeeded = true, transferFocus = false)
      }
    }

    fun setSplitSide(side: RelativePosition) {
      if (side != activeWindow!!.painter!!.position) {
        activeWindow!!.painter!!.positionChanged(side)
      }
      else {
        val editors = activeWindow!!.getAdjacentEditors()
        if (editors.containsKey(side)) {
          if (!isActive) {
            return
          }
          switchWindow(editors.get(side)!!)
        }
      }
    }
  }

  fun changeOrientation() {
    checkConsistency()
    val parent = panel.parent
    if (parent is Splitter) {
      parent.orientation = !parent.orientation
    }
  }

  private fun findFileEditorIndex(file: VirtualFile): Int {
    val composite = getComposite(file)
    return composite?.let { findCompositeIndex(it) } ?: -1
  }

  fun updateFileIcon(file: VirtualFile, icon: Icon) {
    val composite = getComposite(file) ?: return
    val index = findCompositeIndex(composite)
    if (index < 0) return
    tabbedPane.setIconAt(index, decorateFileIcon(composite, icon))
  }

  fun updateFileName(file: VirtualFile, window: EditorWindow) {
    val index = findFileEditorIndex(file)
    if (index == -1) {
      return
    }

    ReadAction.nonBlocking<String> { EditorTabPresentationUtil.getEditorTabTitle(manager.project, file) }
      .expireWhen { isDisposed }
      .finishOnUiThread(ModalityState.any()) { title: @NlsContexts.TabTitle String ->
        val index1 = findFileEditorIndex(file)
        if (index1 != -1) {
          setTitleAt(index1, title)
        }
      }
      .submit(NonUrgentExecutor.getInstance())
    setToolTipTextAt(index, if (UISettings.getInstance().showTabsTooltips) manager.getFileTooltipText(file, window) else null)
  }

  fun unsplit(setCurrent: Boolean) {
    checkConsistency()
    val splitter = panel.parent as? Splitter ?: return
    var compositeToSelect = selectedComposite
    val siblings = getSiblings()
    val parent = splitter.parent as JPanel
    for (eachSibling in siblings) {
      // selected editors will be added first
      val selected = eachSibling.selectedComposite
      if (compositeToSelect == null && selected != null) {
        compositeToSelect = selected
        break
      }
    }
    // we'll select and focus a single editor in the end
    val openOptions = FileEditorOpenOptions(selectAsCurrent = false, requestFocus = false)
    for (sibling in siblings) {
      for (siblingEditor in sibling.composites.toList()) {
        if (compositeToSelect == null) {
          compositeToSelect = siblingEditor
        }
        processSiblingComposite(siblingEditor, openOptions)
      }
      LOG.assertTrue(sibling != this)
      sibling.dispose()
    }
    parent.remove(splitter)
    parent.add(tabbedPane.component, BorderLayout.CENTER)
    parent.revalidate()
    panel = parent
    if (compositeToSelect != null) {
      setSelectedComposite(compositeToSelect, true)
    }
    if (setCurrent) {
      owner.setCurrentWindow(this, false)
    }
    normalizeProportionsIfNeed(panel)
  }

  private fun processSiblingComposite(composite: EditorComposite, openOptions: FileEditorOpenOptions) {
    if (tabCount < UISettings.getInstance().state.editorTabLimit && getComposite(composite.file) == null) {
      setComposite(composite, openOptions)
    }
    else {
      manager.disposeComposite(composite)
    }
  }

  fun unsplitAll() {
    checkConsistency()
    while (inSplitter()) {
      unsplit(true)
    }
  }

  fun inSplitter(): Boolean {
    checkConsistency()
    return panel.parent is Splitter
  }

  val selectedFile: VirtualFile?
    get() {
      checkConsistency()
      return selectedComposite?.file
    }

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getComposite(VirtualFile)}", ReplaceWith("getComposite(file)"))
  fun findFileComposite(file: VirtualFile): EditorWithProviderComposite? {
    return getComposite(file) as EditorWithProviderComposite?
  }

  fun getComposite(inputFile: VirtualFile): EditorComposite? {
    var file = inputFile
    if (file is BackedVirtualFile) {
      file = file.originFile
    }
    for (i in 0 until tabCount) {
      val composite = getCompositeAt(i)
      if (composite.file == file) {
        return composite
      }
    }
    return null
  }

  private fun findComponentIndex(component: Component): Int {
    for (i in 0 until tabCount) {
      val composite = getCompositeAt(i)
      if (composite.component == component) {
        return i
      }
    }
    return -1
  }

  private fun findPreviewIndex(): Int {
    for (i in tabCount - 1 downTo 0) {
      val composite = getCompositeAt(i)
      if (composite.isPreview) {
        return i
      }
    }
    return -1
  }

  fun findCompositeIndex(composite: EditorComposite): Int {
    for (i in 0 until tabCount) {
      val compositeAt = getCompositeAt(i)
      if (compositeAt == composite) {
        return i
      }
    }
    return -1
  }

  fun findFileIndex(fileToFind: VirtualFile): Int {
    for (i in 0 until tabCount) {
      val file = getFileAt(i)
      if (file == fileToFind) {
        return i
      }
    }
    return -1
  }

  private fun getCompositeAt(i: Int): EditorComposite = (tabbedPane.getComponentAt(i) as EditorWindowTopComponent).composite

  fun isFileOpen(file: VirtualFile): Boolean = getComposite(file) != null

  fun isFilePinned(file: VirtualFile): Boolean {
    return (getComposite(file) ?: throw IllegalArgumentException("file is not open: ${file.path}")).isPinned
  }

  fun setFilePinned(file: VirtualFile, pinned: Boolean) {
    val composite = getComposite(file) ?: throw IllegalArgumentException("file is not open: ${file.path}")
    val wasPinned = composite.isPinned
    composite.isPinned = pinned
    if (composite.isPreview && pinned) {
      composite.isPreview = false
      owner.updateFileColor(file)
    }
    if (wasPinned != pinned && ApplicationManager.getApplication().isDispatchThread) {
      ObjectUtils.consumeIfCast(tabbedPane.tabs, JBTabsImpl::class.java) { obj: JBTabsImpl -> obj.doLayout() }
    }
  }

  fun trimToSize(fileToIgnore: VirtualFile?, transferFocus: Boolean) {
    manager.getReady(this).doWhenDone {
      if (!isDisposed) {
        doTrimSize(fileToIgnore, UISettings.getInstance().state.closeNonModifiedFilesFirst, transferFocus)
      }
    }
  }

  private fun doTrimSize(fileToIgnore: VirtualFile?, closeNonModifiedFilesFirst: Boolean, transferFocus: Boolean) {
    val limit = tabLimit
    val closingOrder = getTabClosingOrder(closeNonModifiedFilesFirst)
    val selectedFile = selectedFile
    if (selectedFile != null && shouldCloseSelected(selectedFile, fileToIgnore)) {
      defaultCloseFile(selectedFile, transferFocus)
      closingOrder.remove(selectedFile)
    }

    // close all preview tabs
    val previews = composites.filter { it.isPreview }.map { it.file }.distinct().toList()
    for (preview in previews) {
      if (preview != fileToIgnore) {
        defaultCloseFile(preview, transferFocus)
      }
    }
    for (file in closingOrder) {
      if (tabbedPane.tabCount <= limit || tabbedPane.tabCount == 0 || areAllTabsPinned(fileToIgnore)) {
        return
      }
      if (fileCanBeClosed(file, fileToIgnore)) {
        defaultCloseFile(file, transferFocus)
      }
    }
  }

  private fun getTabClosingOrder(closeNonModifiedFilesFirst: Boolean): MutableSet<VirtualFile> {
    val allFiles = files
    val histFiles = EditorHistoryManager.getInstance(manager.project).fileList
    val closingOrder = LinkedHashSet<VirtualFile>()

    // first, we search for files not in history
    for (file in allFiles) {
      if (!histFiles.contains(file)) {
        closingOrder.add(file)
      }
    }
    if (closeNonModifiedFilesFirst) {
      // Search in history
      for (file in histFiles) {
        val composite = getComposite(file)
        if (composite != null && !owner.manager.isChanged(composite)) {
          // we found non modified file
          closingOrder.add(file)
        }
      }

      // Search in tabbed pane
      for (i in 0 until tabbedPane.tabCount) {
        val file = getFileAt(i)
        if (!owner.manager.isChanged(getCompositeAt(i))) {
          // we found non modified file
          closingOrder.add(file)
        }
      }
    }

    // If it's not enough to close non-modified files only, try all other files.
    // Search in history from less frequently used.
    closingOrder.addAll(histFiles)

    // finally, close tabs by their order
    for (i in 0 until tabbedPane.tabCount) {
      closingOrder.add(getFileAt(i))
    }
    val selectedFile = selectedFile
    // selected should be closed last
    if (selectedFile != null) {
      closingOrder.remove(selectedFile)
      closingOrder.add(selectedFile)
    }
    return closingOrder
  }

  private fun shouldCloseSelected(file: VirtualFile, fileToIgnore: VirtualFile?): Boolean {
    if (!UISettings.getInstance().reuseNotModifiedTabs || !owner.manager.project.isInitialized) {
      return false
    }
    if (!isFileOpen(file) || isFilePinned(file)) {
      return false
    }
    if (file == fileToIgnore) return false
    val composite = getComposite(file) ?: return false
    //Don't check focus in unit test mode
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val owner = IdeFocusManager.getInstance(owner.manager.project).focusOwner
      if (owner == null || !SwingUtilities.isDescendingFrom(owner, composite.selectedEditor.component)) return false
    }
    return !owner.manager.isChanged(composite)
  }

  private fun areAllTabsPinned(fileToIgnore: VirtualFile?): Boolean {
    for (i in tabbedPane.tabCount - 1 downTo 0) {
      if (fileCanBeClosed(getFileAt(i), fileToIgnore)) {
        return false
      }
    }
    return true
  }

  private fun defaultCloseFile(file: VirtualFile, transferFocus: Boolean) {
    closeFile(file = file, disposeIfNeeded = true, transferFocus = transferFocus)
  }

  private fun fileCanBeClosed(file: VirtualFile, fileToIgnore: VirtualFile?): Boolean {
    if (file is BackedVirtualFile) {
      val backedVirtualFile = file as BackedVirtualFile
      val originalFile = backedVirtualFile.originFile
      if (originalFile == fileToIgnore) {
        return false
      }
    }
    return isFileOpen(file) && file != fileToIgnore && !isFilePinned(file)
  }

  internal fun getFileAt(i: Int): VirtualFile = getCompositeAt(i).file

  override fun toString() = "EditorWindow(files=${files.joinToString()})"
}

private fun shouldReservePreview(file: VirtualFile, options: FileEditorOpenOptions, project: Project): Boolean {
  return when {
    !UISettings.getInstance().openInPreviewTabIfPossible -> false
    FileEditorManagerImpl.FORBID_PREVIEW_TAB.get(file, false) -> false
    options.usePreviewTab -> true
    !options.selectAsCurrent || options.requestFocus -> false
    else -> {
      val focusOwner = IdeFocusManager.getInstance(project).focusOwner ?: return false
      hasClientPropertyInHierarchy(focusOwner, FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB)
    }
  }
}

private fun hasClientPropertyInHierarchy(owner: Component, @Suppress("SameParameterValue") propertyKey: Key<Boolean>): Boolean {
  var component = owner
  while (true) {
    if (component is JComponent && component.getClientProperty(propertyKey) == true) {
      return true
    }

    component = component.parent ?: break
  }
  return false
}

internal class EditorWindowTopComponent(@JvmField val window: EditorWindow,
                                        @JvmField val composite: EditorComposite) : JPanel(BorderLayout()), DataProvider, EditorWindowHolder {
  init {
    add(composite.component, BorderLayout.CENTER)
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        ApplicationManager.getApplication().invokeLater {
          if (!hasFocus()) {
            return@invokeLater
          }
          val focus = composite.selectedWithProvider.fileEditor.preferredFocusedComponent
          if (focus != null && !focus.hasFocus()) {
            IdeFocusManager.getGlobalInstance().requestFocus(focus, true)
          }
        }
      }
    })
    focusTraversalPolicy = object : FocusTraversalPolicy() {
      override fun getComponentAfter(aContainer: Container, aComponent: Component) = composite.focusComponent

      override fun getComponentBefore(aContainer: Container, aComponent: Component) = composite.focusComponent

      override fun getFirstComponent(aContainer: Container) = composite.focusComponent

      override fun getLastComponent(aContainer: Container) = composite.focusComponent

      override fun getDefaultComponent(aContainer: Container) = composite.focusComponent
    }
    isFocusCycleRoot = true
  }

  override fun getEditorWindow(): EditorWindow = window

  override fun getData(dataId: String): Any? {
    return when {
      CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> composite.file.takeIf { it.isValid }
      else -> if (CommonDataKeys.PROJECT.`is`(dataId)) composite.project else null
    }
  }
}

private fun shouldHideTabs(composite: EditorComposite?): Boolean {
  return composite != null && composite.allEditors.any { EditorWindow.HIDE_TABS.get(it, false) }
}

private fun decorateFileIcon(composite: EditorComposite, baseIcon: Icon): Icon? {
  val settings = UISettings.getInstance()
  val showAsterisk = settings.markModifiedTabsWithAsterisk && composite.isModified
  val showFileIconInTabs = settings.showFileIconInTabs
  if (ExperimentalUI.isNewUI() || !showAsterisk) {
    return if (showFileIconInTabs) baseIcon else null
  }
  val modifiedIcon = IconUtil.cropIcon(AllIcons.General.Modified, JBRectangle(3, 3, 7, 7))
  val result = LayeredIcon(2)
  if (showFileIconInTabs) {
    result.setIcon(baseIcon, 0)
    result.setIcon(modifiedIcon, 1, -modifiedIcon.iconWidth / 2, 0)
  }
  else {
    result.setIcon(EmptyIcon.create(modifiedIcon.iconWidth, baseIcon.iconHeight), 0)
    result.setIcon(modifiedIcon, 1, 0, 0)
  }
  return scaleIcon(result)
}