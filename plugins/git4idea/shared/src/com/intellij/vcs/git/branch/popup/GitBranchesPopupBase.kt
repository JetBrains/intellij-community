// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.dvcs.branch.BranchType
import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.TreeState
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.util.*
import com.intellij.ui.*
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.popup.NextStepHandler
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.SwingUndoUtil.getUndoManager
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.git.branch.tree.*
import com.intellij.vcs.git.branch.tree.GitBranchesTreeModel.*
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.overrideBuiltInAction
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.selectFirst
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.selectLast
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.selectNext
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.selectPrev
import git4idea.GitBranch
import git4idea.GitDisposable
import git4idea.GitReference
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.*
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.math.min
import kotlin.reflect.KClass

@OptIn(FlowPreview::class)
@ApiStatus.Internal
abstract class GitBranchesPopupBase<T : GitBranchesPopupStepBase>(
  project: Project,
  step: T,
  parent: JBPopup? = null,
  parentValue: Any? = null,
  dimensionServiceKey: String,
) : WizardPopup(project, parent, step), NextStepHandler, GitBranchesPopup {
  protected lateinit var tree: Tree
    private set

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  protected val treeStep: T
    get() = step as T

  protected val popupScope: CoroutineScope =
    GitDisposable.getInstance(project).childScope("Git Branches Tree Popup").also {
      Disposer.register(this) { it.cancel() }
    }

  final override var userResized: Boolean
    private set

  final override var groupByPrefix: Boolean
    get() = treeStep.treeModel.isPrefixGrouping
    set(value) {
      treeStep.treeModel.isPrefixGrouping = value
    }

  private val expandedPaths = HashSet<TreePath>()

  protected val am = ActionManager.getInstance()

  private val searchPatternStateFlow = MutableStateFlow<String?>(null)

  init {
    setParentValue(parentValue)
    minimumSize = if (isNewUI) JBDimension(375, 300 + NEW_UI_MIN_HEIGHT_DELTA) else JBDimension(300, 200)
    this.dimensionServiceKey = if (isNestedPopup()) null else dimensionServiceKey
    userResized = !isNestedPopup() && WindowStateService.getInstance(project).getSizeFor(project, dimensionServiceKey) != null
    closePopupOnTopLevelActionsShortcuts(step.treeModel)
    if (!isNestedPopup()) {
      setSpeedSearchAlwaysShown()
      if (!isNewUI) installTitleToolbar()
      installResizeListener()
    }

    setUiDataProvider { sink ->
      sink[GitBranchesPopupKeys.POPUP] = this@GitBranchesPopupBase
      sink[GitBranchesPopupKeys.AFFECTED_REPOSITORIES] = treeStep.affectedRepositories
      sink[GitBranchesPopupKeys.SELECTED_REPOSITORY] = treeStep.selectedRepository
    }

    popupScope.launch {
      searchPatternStateFlow.drop(1).debounce(GitBranchesTreeUtil.FILTER_DEBOUNCE_MS).collectLatest { pattern ->
        withContext(Dispatchers.EDT) {
          applySearchPattern(pattern)
        }
      }
    }

    subscribeOnUpdates(popupScope, project, step)
  }

  protected abstract fun getSearchFiledEmptyText(): @Nls String

  protected abstract fun getTreeEmptyText(searchPattern: String?): @Nls String

  @VisibleForTesting
  abstract fun createRenderer(): GitBranchesTreeRenderer

  protected open fun createNextStepPopup(nextStep: PopupStep<*>?, parentValue: Any): WizardPopup =
    createPopup(this, nextStep, parentValue)

  /**
   * Returns true if handling should be stopped
   */
  protected open fun handleIconClick(userObject: Any?): Boolean = false

  final override fun setHeaderComponent(c: JComponent?) {
    mySpeedSearchPatternField.textEditor.apply {
      emptyText.text = getSearchFiledEmptyText()
      TextComponentEmptyText.setupPlaceholderVisibility(mySpeedSearchPatternField.textEditor)
    }

    val headerComponent = if (isNewUI) {
      getNewUiHeaderComponent(c)
    }
    else {
      getOldUiHeaderComponent(c)
    }
    super.setHeaderComponent(headerComponent)
  }

  protected open fun getNewUiHeaderComponent(c: JComponent?): JComponent? {
    val toolbar = getHeaderToolbar()?.component?.apply {
      border = JBUI.Borders.emptyLeft(6)
    }

    val searchBorder = mySpeedSearchPatternField.border
    mySpeedSearchPatternField.border = null

    val topPanel = BorderLayoutPanel().apply {
      val dragArea = simplePanel().apply {
        preferredSize = Dimension(0, DRAG_AREA_HEIGHT)
        background = JBUI.CurrentTheme.Popup.BACKGROUND
        isOpaque = true
      }
      addToCenter(dragArea)
      background = JBUI.CurrentTheme.Popup.BACKGROUND
      border = JBUI.Borders.empty(DRAG_AREA_TOP_AND_BOTTOM_BORDER, 0)

      WindowMoveListener(this).installTo(this)
    }

    val panel = BorderLayoutPanel()
      .addToCenter(mySpeedSearchPatternField)
      .apply {
        if (toolbar != null) {
          addToRight(toolbar)
        }
        border = searchBorder
        background = JBUI.CurrentTheme.Popup.BACKGROUND
      }

    val headerComponent = BorderLayoutPanel()
      .addToTop(topPanel)
      .addToCenter(panel)
      .apply {
        background = JBUI.CurrentTheme.Popup.BACKGROUND
      }

    return headerComponent
  }

  protected open fun getOldUiHeaderComponent(c: JComponent?): JComponent? = c

  final override fun createContent(): JComponent {
    return installTree()
  }

  protected open fun installTree(): Tree {
    tree = BranchesTree(treeStep.treeModel).also {
      configureTreePresentation(it)
      overrideTreeActions(it)
      addTreeListeners(it)
    }
    speedSearch.installSupplyTo(tree, false)

    return tree
  }

  protected fun isNestedPopup() = parent != null

  private fun applySearchPattern(pattern: String?) {
    treeStep.updateTreeModelIfNeeded(tree, pattern)
    treeStep.setSearchPattern(pattern)
    val haveBranches = traverseNodesAndExpand()
    selectPreferred()
    if (haveBranches) {
      expandPreviouslyExpandedBranches()
    }
    val model = tree.model
    super.updateSpeedSearchColors(model.getChildCount(model.root) == 0)
    if (!pattern.isNullOrBlank()) {
      tree.emptyText.text = getTreeEmptyText(pattern)
    }
  }

  private fun expandPreviouslyExpandedBranches() {
    expandedPaths.toSet().forEach { path -> TreeUtil.promiseExpand(tree, path) }
  }

  private fun traverseNodesAndExpand(): Boolean {
    val model = tree.model
    var haveBranches = false

    TreeUtil.treeTraverser(tree)
      .filter(BranchType::class or RefTypeUnderRepository::class or GitBranch::class or RefUnderRepository::class)
      .forEach { node ->
        if (!haveBranches && !model.isLeaf(node)) {
          haveBranches = true
        }

        val nodeToExpand = when {
          node is GitBranch && isNestedPopup() && treeStep.affectedRepositories.any { it.state.isCurrentRef(node) } -> node
          node is GitBranch && !isNestedPopup() && treeStep.affectedRepositories.all { it.state.isCurrentRef(node) } -> node
          node is GitBranch && treeStep.affectedRepositories.any {
            node in if (!GitVcsSettings.getInstance (project).showRecentBranches()) emptyList()
            else it.state.recentBranches
          } -> node
          node is RefUnderRepository && node.repository.state.isCurrentRef(node.ref) -> node
          node is RefTypeUnderRepository -> node
          else -> null
        }

        if (nodeToExpand != null) {
          treeStep.createTreePathFor(nodeToExpand)?.let { path -> TreeUtil.promiseExpand(tree, path) }
        }
      }

    return haveBranches
  }

  private infix fun <T : Any> Condition<Any>.or(other: KClass<T>): Condition<Any> =
    Conditions.or(this, Conditions.instanceOf(other.java))

  private infix fun <F : Any, S : Any> KClass<F>.or(other: KClass<S>): Condition<Any> =
    Conditions.or(Conditions.instanceOf(this.java), Conditions.instanceOf(other.java))

  override fun restoreDefaultSize() {
    userResized = false
    WindowStateService.getInstance(project).putSizeFor(project, dimensionServiceKey, null)
    pack(true, true)
  }

  private fun installResizeListener() {
    addResizeListener({ userResized = true }, this)
  }

  final override fun storeDimensionSize() {
    if (userResized) {
      super.storeDimensionSize()
    }
  }

  private fun installSpeedSearchActions() {
    val updateSpeedSearch = {
      val textInEditor = mySpeedSearchPatternField.textEditor.text
      speedSearch.updatePattern(textInEditor)
      onSpeedSearchPatternChanged()
    }
    val group = am.getAction(GitBranchesPopupActions.SPEED_SEARCH_ACTION_GROUP) as DefaultActionGroup
    val speedSearchDataContext = CustomizedDataContext.withSnapshot(DataManager.getInstance().getDataContext(mySpeedSearchPatternField.textEditor)) { sink ->
      sink[CommonDataKeys.PROJECT] = project
    }
    for (action in group.getChildren(am)) {
      registerShortcutAction(action, closePopup = false, dataContext = speedSearchDataContext, afterActionPerformed = updateSpeedSearch)
    }

    registerUndoRedo(updateSpeedSearch)
  }

  private fun registerUndoRedo(updateSpeedSearch: () -> Unit) {
    val undo = am.getAction(IdeActions.ACTION_UNDO)
    registerAction(IdeActions.ACTION_UNDO, KeymapUtil.getKeyStroke(undo.shortcutSet), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        val undoManager = getUndoManager(mySpeedSearchPatternField.textEditor) ?: return
        if (undoManager.canUndo()) {
          undoManager.undo()
          updateSpeedSearch()
        }
      }
    })

    val redo = am.getAction(IdeActions.ACTION_REDO)
    registerAction(IdeActions.ACTION_REDO, KeymapUtil.getKeyStroke(redo.shortcutSet), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        val undoManager = getUndoManager(mySpeedSearchPatternField.textEditor) ?: return
        if (undoManager.canRedo()) {
          undoManager.redo()
          updateSpeedSearch()
        }
      }
    })
  }

  private fun closePopupOnTopLevelActionsShortcuts(model: TreeModel) {
    val dataContext = createShortcutActionDataContext()
    TreeUtil.nodeChildren(model.root, model).forEach { child ->
      val actionItem = child as? PopupFactoryImpl.ActionItem ?: return@forEach
      registerShortcutAction(actionItem.action, closePopup = true, dataContext = dataContext)
    }
  }

  protected abstract fun createShortcutActionDataContext(): DataContext

  private fun installTitleToolbar() {
    val toolbar = getHeaderToolbar() ?: return
    title.setButtonComponent(object : ActiveComponent.Adapter() {
      override fun getComponent(): JComponent = toolbar.component
    }, JBUI.Borders.emptyRight(2))
  }

  abstract fun getHeaderToolbar(): ActionToolbar?

  /**
   * Intercepts [action] when the associated shortcut is pressed, passing explicitly specified [dataContext]
   * and invoking [afterActionPerformed] callback.
   *
   * Note that a check if the action is disabled is not performed.
   */
  private fun registerShortcutAction(
    action: AnAction,
    closePopup: Boolean,
    dataContext: DataContext,
    afterActionPerformed: (() -> Unit)? = null,
  ) {
    val keyStroke = KeymapUtil.getKeyStroke(action.shortcutSet) ?: return

    val actionPlace =
      if (isNestedPopup()) GitBranchesPopupActions.NESTED_POPUP_ACTION_PLACE
      else GitBranchesPopupActions.MAIN_POPUP_ACTION_PLACE

    val wrappedAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (closePopup) {
          cancel()
          parent?.cancel()
        }
        ActionUtil.invokeAction(action, dataContext, actionPlace, null, afterActionPerformed)
      }
    }

    registerAction(am.getId(action), keyStroke, wrappedAction)
  }

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    val topBorder = if (step.title.isNullOrEmpty()) JBUIScale.scale(5) else 0
    border = JBUI.Borders.empty(topBorder, JBUIScale.scale(22), 0, 0)
    background = JBUI.CurrentTheme.Popup.BACKGROUND

    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    val renderer = createRenderer()

    ClientProperty.put(this, Control.CUSTOM_CONTROL, Function { renderer.getLeftTreeIconRenderer(it) })

    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    isRootVisible = false
    showsRootHandles = true
    visibleRowCount = min(calculateTopLevelVisibleRows(), 20)

    cellRenderer = renderer

    accessibleContext.accessibleName = GitBundle.message("git.branches.popup.tree.accessible.name")

    ClientProperty.put(this, DefaultTreeUI.LARGE_MODEL_ALLOWED, true)
    rowHeight = treeRowHeight
    isLargeModel = true
    expandsSelectedPaths = true
    // There are no actions available for grouping nodes in the tree, so the only reason to click
    // is to expand/collapse them.
    toggleClickCount = 1
    SmartExpander.installOn(this)
  }

  /**
   * Local branches would be expanded by [GitBranchesTreePopupStep.getPreferredSelection].
   */
  private fun JTree.calculateTopLevelVisibleRows() =
    model.getChildCount(model.root) + model.getChildCount(
      if (model is GitBranchesTreeSingleRepoModel
          && GitVcsSettings.getInstance(treeStep.project).showRecentBranches()) GitBranchType.RECENT
      else GitBranchType.LOCAL)

  private fun overrideTreeActions(tree: JTree) = with(tree) {
    overrideBuiltInAction("toggle") {
      val path = selectionPath
      if (path != null && model.getChildCount(path.lastPathComponent) == 0) {
        handleSelect(true, null)
        true
      }
      else false
    }

    overrideBuiltInAction(TreeActions.Left.ID) {
      if (isNestedPopup()) {
        cancel()
        true
      }
      else false
    }

    overrideBuiltInAction(TreeActions.Right.ID) {
      val path = selectionPath
      if (path != null && (path.lastPathComponent is RepositoryNode || model.getChildCount(path.lastPathComponent) == 0)) {
        handleSelect(false, null)
        true
      }
      else false
    }

    overrideBuiltInAction(TreeActions.Down.ID) {
      if (speedSearch.isHoldingFilter) selectNext(project)
      else false
    }

    overrideBuiltInAction(TreeActions.Up.ID) {
      if (speedSearch.isHoldingFilter) selectPrev(project)
      else false
    }

    overrideBuiltInAction(TreeActions.Home.ID) {
      if (speedSearch.isHoldingFilter) selectFirst(project)
      else false
    }

    overrideBuiltInAction(TreeActions.End.ID) {
      if (speedSearch.isHoldingFilter) selectLast(project)
      else false
    }
  }

  final override fun processKeyEvent(e: KeyEvent) {
    when {
      e.keyCode == KeyEvent.VK_DOWN || e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_ENTER -> {
        tree.requestFocus()
        tree.dispatchEvent(e)
      }
      Character.isWhitespace(e.keyChar) -> {
        e.consume()
      }
      KeymapUtil.isEventForAction(e, IdeActions.ACTION_FIND) -> {
        mySpeedSearchPatternField.textEditor.requestFocus()
        e.consume()
      }
      mySpeedSearchPatternField.isShowing && mySpeedSearchPatternField.textEditor.hasFocus() && !e.isConsumed -> {
        mySpeedSearchPatternField.dispatchEvent(e)
        if (e.isConsumed) {
          mySpeedSearch.updatePattern(mySpeedSearchPatternField.text)
          mySpeedSearch.update()
        }
      }
      else -> mySpeedSearch.processKeyEvent(e)
    }
  }

  private fun addTreeListeners(tree: JTree) = with(tree) {
    addMouseMotionListener(SelectionMouseMotionListener())
    addMouseListener(SelectOnClickListener())
    addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        expandedPaths.add(event.path)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        expandedPaths.remove(event.path)
      }
    })
  }

  final override fun getActionMap(): ActionMap = tree.actionMap

  final override fun getInputMap(): InputMap = tree.inputMap

  override fun afterShow() {
    selectPreferred()
    traverseNodesAndExpand()
    if (treeStep.isSpeedSearchEnabled) {
      installSpeedSearchActions()
    }
  }

  final override fun updateSpeedSearchColors(error: Boolean) {} // update colors only after branches tree model update

  private fun selectPreferred() {
    val preferredSelection = treeStep.getPreferredSelection()
    if (preferredSelection != null) {
      tree.makeVisible(preferredSelection)
      // If the preferred selection exists in the tree
      if (tree.getRowForPath(preferredSelection) != -1) {
        tree.selectionPath = preferredSelection
        scrollToSelectionIfNeeded(preferredSelection)
        return
      }
    }
    TreeUtil.promiseSelectFirstLeaf(tree)
  }

  private fun scrollToSelectionIfNeeded(selectedPath: TreePath) {
    val selectedNodeBounds = tree.getPathBounds(selectedPath) ?: return
    // If the selected node is not visible
    if (!tree.visibleRect.intersects(selectedNodeBounds)) {
      TreeUtil.scrollToVisible(tree, selectedPath, false)
    }
  }

  final override fun isResizable(): Boolean = true

  private inner class SelectionMouseMotionListener : MouseMotionAdapter() {
    private var lastMouseLocation: Point? = null

    /**
     * this method should be changed only in par with
     * [com.intellij.ui.popup.list.ListPopupImpl.MyMouseMotionListener.isMouseMoved]
     */
    private fun isMouseMoved(location: Point): Boolean {
      if (lastMouseLocation == null) {
        lastMouseLocation = location
        return false
      }
      val prev = lastMouseLocation
      lastMouseLocation = location
      return prev != location
    }

    override fun mouseMoved(e: MouseEvent) {
      if (!isMouseMoved(e.locationOnScreen)
          // Don't change the selection on mouse move in the screen reader mode,
          // because it could conflict with screen reader features that move the mouse pointer.
          || ScreenReader.isActive()) return
      val path = getPath(e)
      if (path != null) {
        tree.selectionPath = path
        notifyParentOnChildSelection()
        if (treeStep.isSelectable(TreeUtil.getUserObject(path.lastPathComponent))) {
          tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          if (pendingChildPath == null || pendingChildPath != path) {
            pendingChildPath = path
            restartTimer()
          }
          return
        }
      }
      tree.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    }
  }

  private inner class SelectOnClickListener : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.button != MouseEvent.BUTTON1) return
      val path = getPath(e) ?: return
      val selected = path.lastPathComponent
      if (treeStep.isSelectable(TreeUtil.getUserObject(selected))) {
        handleSelect(true, e)
      }
    }
  }

  private fun getPath(e: MouseEvent): TreePath? = tree.getClosestPathForLocation(e.point.x, e.point.y)

  private fun handleSelect(handleFinalChoices: Boolean, e: MouseEvent?) {
    val selectionPath = tree.selectionPath
    val pathIsAlreadySelected = showingChildPath != null && showingChildPath == selectionPath
    if (pathIsAlreadySelected) return
    pendingChildPath = null
    val selected = tree.lastSelectedPathComponent
    if (selected != null) {
      val userObject = TreeUtil.getUserObject(selected)
      val point = e?.point
      val refUnderRepository = userObject as? RefUnderRepository
      val reference = userObject as? GitReference ?: refUnderRepository?.ref
      if (point != null && reference != null && isMainIconAt(point, reference)) {
        handleIconClick(userObject)
        return
      }
      if (treeStep.isSelectable(userObject)) {
        disposeChildren()

        val hasNextStep = myStep.hasSubstep(userObject)
        if (!hasNextStep && !handleFinalChoices) {
          showingChildPath = null
          return
        }

        val queriedStep = PopupImplUtil.prohibitFocusEventsInHandleSelect().use {
          myStep.onChosen(userObject, handleFinalChoices)
        }

        if (queriedStep == PopupStep.FINAL_CHOICE || !hasNextStep) {
          setFinalRunnable(myStep.finalRunnable)
          setOk(true)
          disposeAllParents(e)
        }
        else {
          showingChildPath = selectionPath
          handleNextStep(queriedStep, selected)
          showingChildPath = null
        }
      }
    }
  }

  override fun handleNextStep(nextStep: PopupStep<*>?, parentValue: Any) {
    val selectionPath = tree.selectionPath ?: return
    val pathBounds = tree.getPathBounds(selectionPath) ?: return
    val point = Point(pathBounds.x, pathBounds.y)
    SwingUtilities.convertPointToScreen(point, tree)
    myChild = createNextStepPopup(nextStep, parentValue)
    myChild.show(content, content.locationOnScreen.x + content.width - STEP_X_PADDING, point.y, true)
  }

  private fun isMainIconAt(point: Point, selected: Any): Boolean {
    val row = tree.getRowForLocation(point.x, point.y)
    val rowBounds = tree.getRowBounds(row) ?: return false
    point.translate(-rowBounds.x, -rowBounds.y)

    val rowComponent = tree.cellRenderer
                         .getTreeCellRendererComponent(tree, selected, true, false, true, row, false) as? JComponent
                       ?: return false
    val iconComponent = UIUtil.uiTraverser(rowComponent)
                          .filter { ClientProperty.get(it, GitBranchesTreeRenderer.MAIN_ICON) == true }
                          .firstOrNull() ?: return false

    // todo: implement more precise check
    return iconComponent.bounds.width >= point.x
  }

  final override fun onSpeedSearchPatternChanged() {
    val currentPrefix = speedSearch.enteredPrefix
    if (currentPrefix?.lastOrNull()?.isWhitespace() == true) {
      speedSearch.updatePattern(currentPrefix.trimEnd())
    }

    searchPatternStateFlow.tryEmit(speedSearch.enteredPrefix.nullize(true))
  }

  final override fun getPreferredFocusableComponent(): JComponent = tree

  final override fun onChildSelectedFor(value: Any) {
    val path = treeStep.createTreePathFor(value) ?: return

    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  fun refresh() {
    applySearchPattern(speedSearch.enteredPrefix.nullize(true))
    mySpeedSearchPatternField.textEditor.emptyText.text = getSearchFiledEmptyText()
  }

  private fun subscribeOnUpdates(scope: CoroutineScope, project: Project, step: T) {
    scope.launch(context = Dispatchers.EDT) {
      GitBranchesTreeUpdater.getInstance(project).updates.collect {
        when (it) {
          GitBranchesTreeUpdate.REFRESH_TAGS -> runPreservingTreeState {
            step.treeModel.updateTags()
          }
          GitBranchesTreeUpdate.REPAINT -> tree.repaint()
          GitBranchesTreeUpdate.REFRESH -> runPreservingTreeState {
            refresh()
          }
        }
      }
    }
  }

  private fun runPreservingTreeState(action: () -> Unit) {
    val state = TreeState.createOn(tree)
    action()
    state.applyTo(tree)
  }

  private inner class BranchesTree(model: TreeModel) : Tree(model) {

    override fun convertValueToText(
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ): String {
      return when (value) {
        null -> ""
        is ItemPresentation -> value.presentableText.orEmpty()
        is GitBranch -> value.name
        else -> treeStep.getNodeText(value) ?: ""
      }
    }
  }

  companion object {
    private const val DRAG_AREA_HEIGHT: Int = 8
    private const val DRAG_AREA_TOP_AND_BOTTOM_BORDER: Int = 2
    private const val NEW_UI_MIN_HEIGHT_DELTA: Int = DRAG_AREA_HEIGHT + 2 * DRAG_AREA_TOP_AND_BOTTOM_BORDER

    private inline val isNewUI
      get() = ExperimentalUI.isNewUI()

    internal val treeRowHeight: Int
      get() = if (isNewUI) JBUI.CurrentTheme.List.rowHeight() else JBUIScale.scale(22)

    @JvmStatic
    internal fun createTreeSeparator(text: @NlsContexts.Separator String? = null) =
      SeparatorWithText().apply {
        caption = text
        border = JBUI.Borders.emptyTop(
          if (text == null) treeRowHeight / 2 else JBUIScale.scale(SeparatorWithText.DEFAULT_H_GAP))
      }
  }

  @TestOnly
  override fun promiseExpandTree(): Promise<*> {
    return TreeUtil.promiseExpandAll(tree)
  }

  @TestOnly
  override fun getExpandedPathsSize(): Int {
    return tree.expandedPaths.size
  }
}
