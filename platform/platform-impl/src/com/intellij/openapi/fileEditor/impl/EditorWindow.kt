// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "PrivatePropertyName")

package com.intellij.openapi.fileEditor.impl

import com.intellij.featureStatistics.fusCollectors.FileEditorCollector
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ReadmeShownUsageCollector.README_OPENED_ON_START_TS
import com.intellij.openapi.project.ReadmeShownUsageCollector.logReadmeClosedIn
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.tabs.TabsUtil
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.Stack
import com.intellij.util.ui.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.time.Instant
import java.util.function.Function
import javax.swing.*
import kotlin.math.roundToInt

private val LOG = logger<EditorWindow>()

class EditorWindow internal constructor(val owner: EditorsSplitters, private val coroutineScope: CoroutineScope) {
  companion object {
    @JvmField
    val DATA_KEY: DataKey<EditorWindow> = DataKey.create("editorWindow")

    @JvmField
    val HIDE_TABS: Key<Boolean> = Key.create("HIDE_TABS")

    // Metadata to support editor tab drag&drop process: initial index
    internal val DRAG_START_INDEX_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor index", -1)

    // Metadata to support an editor tab drag&drop process: hash of source container
    internal val DRAG_START_LOCATION_HASH_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor location hash", 0)

    // Metadata to support editor tab drag&drop process: initial 'pinned' state
    internal val DRAG_START_PINNED_KEY: Key<Boolean> = Key.create("drag start editor pinned state")

    @JvmStatic
    val tabLimit: Int
      get() {
        if (DistractionFreeModeController.isDistractionFreeModeEnabled()
            && DistractionFreeModeController.getStandardTabPlacement() == UISettings.TABS_NONE) {
          return 1
        }
        else {
          return UISettings.getInstance().editorTabLimit
        }
      }
  }

  internal val component: JComponent
    get() = tabbedPane.component

  val tabbedPane: EditorTabbedContainer = EditorTabbedContainer(window = this, coroutineScope = coroutineScope)

  val isDisposed: Boolean
    get() = !coroutineScope.isActive

  private val removedTabs = object : Stack<Pair<String, FileEditorOpenOptions>>() {
    override fun push(pair: Pair<String, FileEditorOpenOptions>) {
      if (size >= tabLimit) {
        removeAt(0)
      }
      super.push(pair)
    }
  }

  val isShowing: Boolean
    get() = component.isShowing

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
    get() = component.size

  private fun checkConsistency() {
    LOG.assertTrue(isValid, "EditorWindow not in collection")
  }

  val isValid: Boolean
    get() = owner.containsWindow(this)

  @Suppress("DEPRECATION")
  @get:Deprecated("Use selectedComposite", ReplaceWith("selectedComposite"), level = DeprecationLevel.ERROR)
  val selectedEditor: EditorWithProviderComposite?
    get() = getContextComposite() as EditorWithProviderComposite?
  val selectedComposite: EditorComposite?
    get() = getContextComposite()

  @Suppress("DEPRECATION")
  @Deprecated("Use getSelectedComposite", ReplaceWith("getSelectedComposite(ignorePopup)"), level = DeprecationLevel.ERROR)
  fun getSelectedEditor(@Suppress("UNUSED_PARAMETER") ignorePopup: Boolean): EditorWithProviderComposite? {
    return getContextComposite() as EditorWithProviderComposite?
  }

  // used externally
  /**
   * @param ignorePopup if `false` and context a menu is shown currently for some tab,
   * composite for which a menu is invoked will be returned
   */
  @Suppress("MemberVisibilityCanBePrivate", "unused")
  fun getSelectedComposite(ignorePopup: Boolean): EditorComposite? {
    return (tabbedPane.getSelectedComponent(ignorePopup) as? EditorWindowTopComponent)?.composite
  }

  /**
   * A composite in a context.
   * For example, if a context menu is shown currently for some tab, the composite for which a menu is invoked will be returned
   */
  fun getContextComposite(): EditorComposite? {
    return (tabbedPane.tabs.targetInfo?.component as? EditorWindowTopComponent)?.composite
  }

  val allComposites: List<EditorComposite>
    get() = getComposites().toList()

  fun getComposites(): Sequence<EditorComposite> = IntRange(0, tabCount - 1).asSequence().map(::getCompositeAt)

  @Suppress("DEPRECATION")
  @get:Deprecated("{@link #getAllComposites()}", ReplaceWith("allComposites)"))
  val editors: Array<EditorWithProviderComposite>
    get() = getComposites().filterIsInstance<EditorWithProviderComposite>().toList().toTypedArray()

  val files: Array<VirtualFile>
    get() = getFileSequence().toList().toTypedArray()

  val fileList: List<VirtualFile>
    get() = getFileSequence().toList()

  @RequiresEdt
  internal fun getFileSequence(): Sequence<VirtualFile> = getComposites().map { it.file }

  init {
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

  internal enum class RelativePosition(val swingConstant: Int) {
    CENTER(SwingConstants.CENTER),
    UP(SwingConstants.TOP),
    LEFT(SwingConstants.LEFT),
    DOWN(SwingConstants.BOTTOM),
    RIGHT(SwingConstants.RIGHT)
  }

  internal fun getAdjacentEditors(): Map<RelativePosition, EditorWindow> {
    checkConsistency()
    // can't have more than 4
    val adjacentEditors = HashMap<RelativePosition, EditorWindow>(4)
    val windows = owner.getOrderedWindows()
    windows.remove(this)

    val panelToWindow = HashMap<JComponent, EditorWindow>()
    for (window in windows) {
      panelToWindow.put(window.component, window)
    }

    val relativePoint = if (ApplicationManager.getApplication().isUnitTestMode) {
      RelativePoint(MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false))
    }
    else {
      RelativePoint(component.locationOnScreen)
    }
    val point = relativePoint.getPoint(owner)
    val nearestComponent: (Int, Int) -> Component? = { x, y ->
      SwingUtilities.getDeepestComponentAt(owner, x, y)
    }

    fun findAdjacentEditor(startComponent: Component?): EditorWindow? {
      var component = startComponent
      while (component !== owner && component != null) {
        panelToWindow.get(component)?.let {
          return it
        }
        component = component.parent ?: break
      }
      return null
    }

    fun biConsumer(window: EditorWindow?, position: RelativePosition) {
      if (window != null) {
        adjacentEditors.put(position, window)
      }
    }

    // Even if the above/below adjacent editor is shifted a bit to the right from the left edge of the current editor,
    // still try to choose an editor that is visually above/below - shifted nor more than a quarter of editor width.
    val x = point.x + component.width / 4
    // Splitter has width of one pixel - we need to step at least 2 pixels to be over an adjacent editor
    val searchStep = 2
    biConsumer(findAdjacentEditor(nearestComponent(x, point.y - searchStep)), RelativePosition.UP)
    biConsumer(findAdjacentEditor(nearestComponent(x, point.y + component.height + searchStep)), RelativePosition.DOWN)
    biConsumer(findAdjacentEditor(nearestComponent(point.x - searchStep, point.y)), RelativePosition.LEFT)
    biConsumer(findAdjacentEditor(nearestComponent(point.x + component.width + searchStep, point.y)), RelativePosition.RIGHT)
    return adjacentEditors
  }

  @Deprecated("{@link #setSelectedComposite(EditorComposite, boolean)}", ReplaceWith("setSelectedComposite(composite, focusEditor)"))
  fun setSelectedEditor(composite: EditorComposite, focusEditor: Boolean) {
    setSelectedComposite(composite, focusEditor)
  }

  fun setSelectedComposite(composite: EditorComposite, focusEditor: Boolean) {
    // select a composite in a tabbed pane and then focus on a composite if needed
    val index = findFileIndex(composite.file)
    if (index != -1 && !isDisposed) {
      tabbedPane.setSelectedIndex(index, focusEditor)
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #setComposite(EditorComposite, boolean)}",
              ReplaceWith("setComposite(editor, FileEditorOpenOptions().withRequestFocus(focusEditor))",
                          "com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions"))
  fun setEditor(@Suppress("DEPRECATION") editor: EditorWithProviderComposite, focusEditor: Boolean) {
    addComposite(editor, FileEditorOpenOptions(requestFocus = focusEditor))
  }

  internal fun setComposite(composite: EditorComposite, focusEditor: Boolean) {
    addComposite(composite, FileEditorOpenOptions(requestFocus = focusEditor, usePreviewTab = composite.isPreview))
  }

  internal fun addComposite(composite: EditorComposite, options: FileEditorOpenOptions) {
    val isNewEditor = findCompositeIndex(composite) == -1
    val isPreviewMode = (isNewEditor || composite.isPreview) && shouldReservePreview(composite.file, options, owner.manager.project)
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
      tabbedPane.insertTab(file = file,
                           icon = emptyIcon,
                           component = EditorWindowTopComponent(window = this, composite = composite),
                           tooltip = null,
                           indexToInsert = indexToInsert,
                           composite = composite,
                           parentDisposable = composite)
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
        else if (composite.isPinned) {
          composite.isPinned = true
        }
      }
      file.putUserData(DRAG_START_LOCATION_HASH_KEY, null)
      file.putUserData(DRAG_START_INDEX_KEY, null)
      file.putUserData(DRAG_START_PINNED_KEY, null)

      if (!AsyncEditorLoader.isOpenedInBulk(composite.file)) {
        trimToSize(fileToIgnore = file, transferFocus = false)
      }

      owner.updateFileIcon(file)
    }

    owner.updateFileColorAsync(composite.file)
    if (!AsyncEditorLoader.isOpenedInBulk(composite.file)) {
      if (options.selectAsCurrent) {
        setSelectedComposite(composite, options.requestFocus)
      }
      updateTabsVisibility()
      owner.validate()
    }
  }

  internal fun selectOpenedCompositeOnStartup(composite: EditorComposite, requestFocus: Boolean) {
    composite.selectedEditor?.selectNotify()
    setSelectedComposite(composite = composite, focusEditor = requestFocus)
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
      val target = getSiblings()[0]
      if (virtualFile != null) {
        syncCaretIfPossible(fileEditorManager.openFileImpl4(window = target,
                                                            _file = virtualFile,
                                                            entry = null,
                                                            options = FileEditorOpenOptions(requestFocus = focusNew)).allEditors)
      }
      return target
    }

    if (tabCount == 0) {
      return null
    }

    val splitter = createSplitter(isVertical = orientation == JSplitPane.VERTICAL_SPLIT, proportion = 0.5f, minProp = 0.1f, maxProp = 0.9f)
    splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)
    val result = EditorWindow(owner = owner, owner.coroutineScope.childScope(CoroutineName("EditorWindow")))
    val selectedComposite = selectedComposite

    swapComponents(parent = component.parent as JPanel, toAdd = splitter, toRemove = component)
    val existingEditor = tabbedPane.component

    if (fileIsSecondaryComponent) {
      splitter.firstComponent = existingEditor
      splitter.secondComponent = result.component
    }
    else {
      splitter.secondComponent = existingEditor
      splitter.firstComponent = result.component
    }
    normalizeProportionsIfNeed(existingEditor)

    // open only selected file in the new splitter instead of opening all tabs
    val nextFile = virtualFile ?: selectedComposite!!.file
    val currentState = selectedComposite?.currentStateAsHistoryEntry()?.takeIf { it.file == nextFile }
    val openOptions = FileEditorOpenOptions(requestFocus = focusNew,
                                            isExactState = true,
                                            pin = isFileOpen(nextFile) && isFilePinned(nextFile))
    val editors = fileEditorManager.openFileImpl4(window = result,
                                                  _file = nextFile,
                                                  entry = currentState,
                                                  options = openOptions).allEditors
    syncCaretIfPossible(editors)
    if (!focusNew) {
      result.setSelectedComposite(composite = selectedComposite!!, focusEditor = true)
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        selectedComposite.focusComponent?.let {
          IdeFocusManager.getGlobalInstance().requestFocus(it, true)
        }
      }
    }
    component.revalidate()
    return result
  }

  private fun normalizeProportionsIfNeed(inputComponent: Container) {
    var component = inputComponent
    if (!AdvancedSettings.getBoolean("editor.normalize.splits")) {
      return
    }

    var isVertical = when {
      component is Splitter -> component.isVertical
      component.components.firstOrNull() is Splitter -> (component.components.first() as Splitter).isVertical
      else -> null
    }

    val hierarchyStack = LinkedHashMap<Splitter, Boolean>()
    while (component !== manager.component) {
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
    @Suppress("DuplicatedCode")
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
    val splitter = (component.parent as? Splitter) ?: return emptyList()
    return owner.getWindowSequence().filter { it != this@EditorWindow && SwingUtilities.isDescendingFrom(it.component, splitter) }.toList()
  }

  fun requestFocus(forced: Boolean) {
    tabbedPane.requestFocus(forced)
  }

  fun toFront() {
    UIUtil.toFront(ComponentUtil.getWindow(tabbedPane.component))
  }

  internal fun updateTabsVisibility(settings: UISettings = UISettings.getInstance()) {
    tabbedPane.tabs.presentation.isHideTabs = (owner.isFloating && tabCount == 1 && shouldHideTabs(selectedComposite)) ||
                                              settings.editorTabPlacement == UISettings.TABS_NONE ||
                                              (settings.presentationMode && !Registry.`is`("ide.editor.tabs.visible.in.presentation.mode"))
  }

  fun closeAllExcept(selectedFile: VirtualFile?) {
    runBulkTabChange(owner) {
      for (file in getFileSequence().toList()) {
        if (file != selectedFile && !isFilePinned(file)) {
          closeFile(file)
        }
      }
    }
  }

  fun dispose() {
    coroutineScope.cancel()
    owner.removeWindow(this)
  }

  fun hasClosedTabs(): Boolean = !removedTabs.empty()

  fun restoreClosedTab() {
    assert(hasClosedTabs()) { "Nothing to restore" }
    val info = removedTabs.pop()
    val file = VirtualFileManager.getInstance().findFileByUrl(info.getFirst()) ?: return
    manager.openFileImpl4(window = this,
                          _file = file,
                          entry = null,
                          options = info.getSecond().copy(selectAsCurrent = true, requestFocus = true))
  }

  fun closeFile(file: VirtualFile, disposeIfNeeded: Boolean = true, @Suppress("UNUSED_PARAMETER") transferFocus: Boolean = true) {
    closeFile(file = file, disposeIfNeeded = disposeIfNeeded)
  }

  @JvmOverloads
  fun closeFile(file: VirtualFile, disposeIfNeeded: Boolean = true) {
    val composite = getComposite(file) ?: return
    closeFile(file = file, composite = composite, disposeIfNeeded = disposeIfNeeded)
  }

  internal fun closeFile(file: VirtualFile, composite: EditorComposite, disposeIfNeeded: Boolean = true) {
    runBulkTabChange(owner) {
      val fileEditorManager = manager
      try {
        fileEditorManager.project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)
          .beforeFileClosed(fileEditorManager, file)
        val componentIndex = findComponentIndex(composite.component)
        // composite could close itself on decomposition
        if (componentIndex >= 0) {
          val indexToSelect = computeIndexToSelect(fileBeingClosed = file, fileIndex = componentIndex)
          removedTabs.push(Pair(file.url, FileEditorOpenOptions(index = componentIndex, pin = composite.isPinned)))
          tabbedPane.removeTabAt(componentIndex, indexToSelect)
          fileEditorManager.disposeComposite(composite)
        }
        if (disposeIfNeeded && tabCount == 0) {
          removeFromSplitter()
          logEmptyStateIfMainSplitter(cause = EmptyStateCause.ALL_TABS_CLOSED)
        }
        else {
          component.revalidate()
        }
      }
      finally {
        val openedTs = file.getUserData(README_OPENED_ON_START_TS)
        if (openedTs != null) {
          val wasOpenedMillis = Instant.now().toEpochMilli() - openedTs.toEpochMilli()
          logReadmeClosedIn(wasOpenedMillis)
          file.removeUserData(README_OPENED_ON_START_TS)
        }

        fileEditorManager.removeSelectionRecord(file, this)
        (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
        fileEditorManager.notifyPublisher {
          val project = fileEditorManager.project
          if (!project.isDisposed) {
            project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(fileEditorManager, file)
          }
        }
        owner.afterFileClosed(file)
      }
    }
  }

  fun logEmptyStateIfMainSplitter(cause: EmptyStateCause) {
    require(tabCount == 0) { "Tab count expected to be zero" }
    if (EditorEmptyTextPainter.isEnabled() && component.parent === manager.mainSplitters) {
      FileEditorCollector.logEditorEmptyState(manager.project, cause)
    }
  }

  fun removeFromSplitter() {
    if (!inSplitter()) {
      return
    }

    if (owner.currentWindow.let { it == this || it == null }) {
      val siblings = getSiblings()
      owner.setCurrentWindow(window = siblings.firstOrNull(), requestFocus = true)
    }

    val splitter = component.parent as Splitter
    val otherComponent = splitter.getOtherComponent(component)

    when (val parent = splitter.parent) {
      is Splitter -> {
        if (parent.firstComponent === splitter) {
          parent.firstComponent = otherComponent
        }
        else {
          parent.secondComponent = otherComponent
        }
        normalizeProportionsIfNeed(owner.currentWindow!!.component)
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
  internal fun computeIndexToSelect(fileBeingClosed: VirtualFile, fileIndex: Int): Int {
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

        val composite = getComposite(histFile) ?: continue
        val histFileIndex = findComponentIndex(composite.component)
        if (histFileIndex >= 0) {
          // if the file being closed is located before the hist file, then after closing, the index of the histFile will be shifted by -1
          return histFileIndex
        }
      }
    }
    else if (uiSettings.activeRightEditorOnClose && fileIndex + 1 < tabbedPane.tabCount) {
      return fileIndex + 1
    }

    // by default, select the previous neighbor
    return if (fileIndex > 0) fileIndex - 1 else -1
  }

  internal interface SplitChooser {
    val position: RelativePosition

    fun positionChanged(position: RelativePosition)

    fun dispose()
  }

  internal fun showSplitChooser(project: Project, showInfoPanel: Boolean): SplitChooser {
    val disposable = Disposer.newDisposable("GlassPaneListeners")
    val painter = MySplitPainter(project, showInfoPanel, tabbedPane, owner)
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      IdeGlassPaneUtil.find(component).addPainter(component, painter, disposable)
    }

    //Reminder about UI components hierarchy:
    //    EditorsSplitters
    //    |-- EditorTabs                    // `tabbedPane.component` OR `component`
    //        |-- EditorWindowTopComponent  //
    //            |-- EditorCompositePanel  // `tabbedPane.getSelectedComposite()`
    //                |-- JPanel            // `componentToFocus`
    //                    |-- PsiAwareTextEditorComponent
    //
    //assuming that it's safe to `!!`, if `showSplitChooser` is called, we expect that selected editor exists and it's not null
    val editorComposite: EditorComposite = tabbedPane.getSelectedComposite()!!
    val componentToFocus: JComponent = editorComposite.component.getComponent(0) as JComponent

    componentToFocus.repaint()
    componentToFocus.isFocusable = true
    componentToFocus.grabFocus()
    componentToFocus.focusTraversalKeysEnabled = false
    val focusAdapter = object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        componentToFocus.removeFocusListener(this)
        val splitterService = SplitterService.getInstance(project)
        if (splitterService.activeWindow == this@EditorWindow) {
          splitterService.stopSplitChooser(true)
        }
      }
    }
    componentToFocus.addFocusListener(focusAdapter)
    return object : SplitChooser {
      override val position: RelativePosition
        get() = painter.position

      override fun positionChanged(position: RelativePosition) {
        painter.positionChanged(position)
      }

      override fun dispose() {
        painter.rectangle = null
        componentToFocus.removeFocusListener(focusAdapter)
        componentToFocus.isFocusable = false
        componentToFocus.repaint()
        Disposer.dispose(disposable)
      }
    }
  }

  fun changeOrientation() {
    checkConsistency()
    val parent = component.parent
    if (parent is Splitter) {
      parent.orientation = !parent.orientation
    }
  }

  fun unsplit(setCurrent: Boolean) {
    checkConsistency()
    val splitter = component.parent as? Splitter ?: return
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

    // we'll select and focus on a single editor in the end
    val openOptions = FileEditorOpenOptions(selectAsCurrent = false, requestFocus = false)
    for (sibling in siblings) {
      for (siblingEditor in sibling.getComposites().toList()) {
        if (compositeToSelect == null) {
          compositeToSelect = siblingEditor
        }
        processSiblingComposite(siblingEditor, openOptions)
      }
      sibling.dispose()
    }

    swapComponents(parent, toAdd = tabbedPane.component, toRemove = splitter)
    parent.revalidate()

    if (compositeToSelect != null) {
      setSelectedComposite(compositeToSelect, true)
    }
    if (setCurrent) {
      owner.setCurrentWindow(window = this, requestFocus = false)
    }
    normalizeProportionsIfNeed(component)
  }

  private fun processSiblingComposite(composite: EditorComposite, openOptions: FileEditorOpenOptions) {
    if (tabCount < UISettings.getInstance().state.editorTabLimit && getComposite(composite.file) == null) {
      addComposite(composite, openOptions)
    }
    else {
      manager.disposeComposite(composite)
    }
  }

  fun unsplitAll() {
    checkConsistency()
    while (inSplitter()) {
      unsplit(setCurrent = true)
    }
  }

  fun inSplitter(): Boolean {
    checkConsistency()
    return component.parent is Splitter
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

  fun getComposite(inputFile: VirtualFile): EditorComposite? = findCompositeAndIndex(inputFile)?.first

  internal fun findCompositeAndIndex(inputFile: VirtualFile): kotlin.Pair<EditorComposite, Int>? {
    val file = (inputFile as? BackedVirtualFile)?.originFile ?: inputFile
    for (i in 0 until tabCount) {
      val composite = getCompositeAt(i)
      if (composite.file == file) {
        return composite to i
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

  internal fun findCompositeIndex(composite: EditorComposite): Int {
    for (i in 0 until tabCount) {
      val compositeAt = getCompositeAt(i)
      if (compositeAt === composite) {
        return i
      }
    }
    return -1
  }

  internal fun findFileIndex(fileToFind: VirtualFile): Int {
    return IntRange(0, tabCount - 1).firstOrNull { getCompositeAt(it).file == fileToFind } ?: -1
  }

  private fun getCompositeAt(i: Int): EditorComposite = (tabbedPane.tabs.getTabAt(i).component as EditorWindowTopComponent).composite

  fun isFileOpen(file: VirtualFile): Boolean = getComposite(file) != null

  fun isFilePinned(file: VirtualFile): Boolean {
    return requireNotNull(getComposite(file)) { "file is not open: $file" }.isPinned
  }

  fun setFilePinned(file: VirtualFile, pinned: Boolean) {
    setFilePinned(composite = requireNotNull(getComposite(file)) { "file is not open: $file" }, pinned = pinned)
  }

  internal fun setFilePinned(composite: EditorComposite, pinned: Boolean) {
    val wasPinned = composite.isPinned
    composite.isPinned = pinned
    if (composite.isPreview && pinned) {
      composite.isPreview = false
      owner.updateFileColorAsync(composite.file)
    }
    if (wasPinned != pinned && ApplicationManager.getApplication().isDispatchThread) {
      (tabbedPane.tabs as? JBTabsImpl)?.doLayout()
    }
  }

  fun trimToSize(fileToIgnore: VirtualFile?, transferFocus: Boolean) {
    if (!isDisposed) {
      doTrimSize(fileToIgnore = fileToIgnore,
                 closeNonModifiedFilesFirst = UISettings.getInstance().state.closeNonModifiedFilesFirst,
                 transferFocus = transferFocus)
    }
  }

  private fun doTrimSize(fileToIgnore: VirtualFile?, closeNonModifiedFilesFirst: Boolean, transferFocus: Boolean) {
    val selectedFile = selectedFile

    val alreadyClosedFile = if (selectedFile != null && shouldCloseSelected(selectedFile, fileToIgnore)) {
      defaultCloseFile(selectedFile, transferFocus)
      selectedFile
    }
    else {
      null
    }

    // close all preview tabs
    for (file in getComposites().filter { it.isPreview }.map { it.file }.filter { it != fileToIgnore }.distinct().toList()) {
      defaultCloseFile(file = file, transferFocus = transferFocus)
    }

    val limit = tabLimit
    fun isUnderLimit(): Boolean = tabbedPane.tabCount <= limit || tabbedPane.tabCount == 0 || !isAnyTabClosable(fileToIgnore)

    if (isUnderLimit()) {
      return
    }

    val closingOrder = getTabClosingOrder(closeNonModifiedFilesFirst)
    for (file in closingOrder) {
      if (isUnderLimit()) {
        return
      }

      if (file != alreadyClosedFile && fileCanBeClosed(file, fileToIgnore)) {
        defaultCloseFile(file, transferFocus)
      }
    }
  }

  private fun getTabClosingOrder(closeNonModifiedFilesFirst: Boolean): MutableSet<VirtualFile> {
    val allFiles = getFileSequence().toList()
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
          // we found a non-modified file
          closingOrder.add(file)
        }
      }

      // Search in tabbed pane
      for (i in 0 until tabbedPane.tabCount) {
        val file = getFileAt(i)
        if (!owner.manager.isChanged(getCompositeAt(i))) {
          // we found a non-modified file
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
    if (file == fileToIgnore) {
      return false
    }
    val composite = getComposite(file) ?: return false
    // don't check focus in unit test mode
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val owner = IdeFocusManager.getInstance(owner.manager.project).focusOwner
      if (owner == null || !SwingUtilities.isDescendingFrom(owner, composite.selectedEditor!!.component)) {
        return false
      }
    }
    return !owner.manager.isChanged(composite)
  }

  private fun isAnyTabClosable(fileToIgnore: VirtualFile?): Boolean {
    return (tabbedPane.tabCount - 1 downTo 0).any { fileCanBeClosed(getFileAt(it), fileToIgnore) }
  }

  private fun defaultCloseFile(file: VirtualFile, transferFocus: Boolean) {
    closeFile(file = file, disposeIfNeeded = true, transferFocus = transferFocus)
  }

  private fun fileCanBeClosed(file: VirtualFile, fileToIgnore: VirtualFile?): Boolean {
    if (file is BackedVirtualFile && file.originFile == fileToIgnore) {
      return false
    }
    return isFileOpen(file) && file != fileToIgnore && !isFilePinned(file) && isClosingAllowed(file)
  }

  private fun isClosingAllowed(file: VirtualFile): Boolean {
    val extensions = EditorAutoClosingHandler.EP_NAME.extensionList
    if (extensions.isEmpty()) return true
    val composite = getComposite(file) ?: return true
    return extensions.all { handler -> handler.isClosingAllowed(composite) }
  }

  internal fun getFileAt(i: Int): VirtualFile = getCompositeAt(i).file

  override fun toString(): String {
    if (EDT.isCurrentThreadEdt()) {
      return "EditorWindow(files=${getComposites().joinToString { it.file.path }})"
    }
    else {
      return super.toString()
    }
  }
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

internal class EditorWindowTopComponent(
  @JvmField val window: EditorWindow,
  @JvmField val composite: EditorComposite,
) : JPanel(BorderLayout()), DataProvider, EditorWindowHolder {
  init {
    add(composite.component, BorderLayout.CENTER)
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        ApplicationManager.getApplication().invokeLater {
          if (!hasFocus()) {
            return@invokeLater
          }
          val focus = composite.selectedWithProvider?.fileEditor?.preferredFocusedComponent
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
      CommonDataKeys.PROJECT.`is`(dataId) -> window.owner.manager.project
      PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.`is`(dataId) -> window.owner.currentCompositeFlow.value?.selectedEditor
      else -> null
    }
  }
}

private fun swapComponents(parent: JPanel, toAdd: JComponent, toRemove: JComponent) {
  if (parent is Splitter) {
    if (parent.firstComponent === toRemove) {
      parent.firstComponent = toAdd
    }
    else {
      check(parent.secondComponent === toRemove)
      parent.secondComponent = toAdd
    }
  }
  else {
    check(parent is EditorsSplitters)
    parent.remove(toRemove)
    parent.add(toAdd, BorderLayout.CENTER)
  }
}

private fun shouldHideTabs(composite: EditorComposite?): Boolean {
  return composite != null && composite.allEditors.any { EditorWindow.HIDE_TABS.get(it, false) }
}

private class MySplitPainter(
  private val project: Project,
  private var showInfoPanel: Boolean,
  private val tabbedPane: EditorTabbedContainer,
  private val owner: EditorsSplitters,
) : AbstractPainter() {
  var rectangle: Shape? = tabbedPane.tabs.dropArea
  var position: EditorWindow.RelativePosition = EditorWindow.RelativePosition.CENTER

  override fun needsRepaint(): Boolean = rectangle != null

  override fun executePaint(component: Component, g: Graphics2D) {
    if (rectangle == null) {
      return
    }

    GraphicsUtil.setupAAPainting(g)
    g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
    g.fill(rectangle)
    if (position == EditorWindow.RelativePosition.CENTER && showInfoPanel) {
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
    val openShortcuts = IdeBundle.message(
      "split.with.chooser.move.tab",
      getShortcut("SplitChooser.Split"),
      if (SplitterService.getInstance(project).initialEditorWindow != null) {
        IdeBundle.message("split.with.chooser.duplicate.tab", getShortcut("SplitChooser.Duplicate"))
      }
      else {
        ""
      })
    val switchShortcuts = IdeBundle.message("split.with.chooser.switch.tab", getShortcut("SplitChooser.NextWindow"))

    // Adjust the default width to an info text
    val font = StartupUiUtil.labelFont
    val fontMetrics = g.getFontMetrics(font)
    val openShortcutsWidth = fontMetrics.stringWidth(openShortcuts)
    val switchShortcutsWidth = fontMetrics.stringWidth(switchShortcuts)
    width = width.coerceAtLeast((openShortcutsWidth.coerceAtLeast(switchShortcutsWidth) * 1.2f).roundToInt())

    // Check if an info panel will actually fit into an editor with some free space around the edges
    if (rectangle.bounds.height < height * 1.2f || rectangle.bounds.width < width * 1.2f) {
      return
    }

    val shape = RoundRectangle2D.Double(centerX - width / 2.0, centerY - height / 2.0, width.toDouble(), height.toDouble(),
                                        arc.toDouble(), arc.toDouble())
    g.color = UIUtil.getLabelBackground()
    g.fill(shape)
    val arrowCenterVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.center")
    val arrowsVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.vertical")
    val arrowsHShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.horizontal")
    val function = Function { icon: Icon -> Point(centerX - icon.iconWidth / 2, centerY - icon.iconHeight / 2 + arrowCenterVShift) }
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

  private fun getShortcut(actionId: String): @NlsSafe String {
    val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)
    return KeymapUtil.getKeystrokeText(shortcut?.firstKeyStroke)
  }

  fun positionChanged(position: EditorWindow.RelativePosition) {
    if (this.position == position) {
      return
    }

    showInfoPanel = false
    this.position = position
    rectangle = null
    setNeedsRepaint(true)
    val r = tabbedPane.tabs.dropArea
    TabsUtil.updateBoundsWithDropSide(r, position.swingConstant)
    rectangle = Rectangle2D.Double(r.x.toDouble(), r.y.toDouble(), r.width.toDouble(), r.height.toDouble())
  }
}