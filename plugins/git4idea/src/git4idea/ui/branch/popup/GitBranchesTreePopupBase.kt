// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchSyncPolicyUpdateNotifier
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.ide.util.treeView.TreeState
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.concurrency.waitForPromise
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
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
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitVcs
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.*
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.branch.tree.GitBranchesTreeModel.*
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import git4idea.ui.branch.tree.GitBranchesTreeRenderer.Companion.getText
import git4idea.ui.branch.tree.GitBranchesTreeSingleRepoModel
import git4idea.ui.branch.tree.GitBranchesTreeUtil.overrideBuiltInAction
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectFirst
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectLast
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectNext
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectPrev
import git4idea.ui.branch.tree.recentCheckoutBranches
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.Cursor
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
import kotlin.time.Duration

abstract class GitBranchesTreePopupBase<T : GitBranchesTreePopupStepBase>(
  project: Project,
  step: T,
  parent: JBPopup? = null,
  parentValue: Any? = null,
  dimensionServiceKey: String,
) : WizardPopup(project, parent, step), TreePopup, NextStepHandler {
  protected lateinit var tree: Tree
    private set

  private val treeStateHolder = project.service<GitBranchesPopupTreeStateHolder>()

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  protected val treeStep: T
    get() = step as T

  private lateinit var searchPatternStateFlow: MutableStateFlow<String?>

  internal var userResized: Boolean
    private set

  private val expandedPaths = HashSet<TreePath>()

  protected val am = ActionManager.getInstance()
  private val findKeyStroke = KeymapUtil.getKeyStroke(am.getAction("Find").shortcutSet)

  init {
    setParentValue(parentValue)
    minimumSize = if (isNewUI) JBDimension(375, 300) else JBDimension(300, 200)
    this.dimensionServiceKey = if (isChild()) null else dimensionServiceKey
    userResized = !isChild() && WindowStateService.getInstance(project).getSizeFor(project, dimensionServiceKey) != null
    installShortcutActions(step.treeModel)
    if (!isChild()) {
      setSpeedSearchAlwaysShown()
      if (!isNewUI) installTitleToolbar()
      installRepoListener()
      installResizeListener()
      installTagsListener(step.treeModel)
      DvcsBranchSyncPolicyUpdateNotifier(project, GitVcs.getInstance(project),
                                         GitVcsSettings.getInstance(project), GitRepositoryManager.getInstance(project))
        .initBranchSyncPolicyIfNotInitialized()
    }
    installBranchSettingsListener()
    setDataProvider(EdtNoGetDataProvider { sink ->
      sink[POPUP_KEY] = this@GitBranchesTreePopupBase
      sink[GitBranchActionsUtil.REPOSITORIES_KEY] = treeStep.repositories
    })
  }

  protected abstract fun getSearchFiledEmptyText(): @Nls String

  protected abstract fun getTreeEmptyText(searchPattern: String?): @Nls String

  protected abstract fun createRenderer(treeStep: T): GitBranchesTreeRenderer

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
    } else {
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

    val panel = BorderLayoutPanel()
      .addToCenter(mySpeedSearchPatternField)
      .apply {
        if (toolbar != null) {
          addToRight(toolbar)
        }
        border = searchBorder
        background = JBUI.CurrentTheme.Popup.BACKGROUND
      }

    return panel
  }

  protected open fun getOldUiHeaderComponent(c: JComponent?): JComponent? = c

  override fun createContent(): JComponent {
    tree = BranchesTree(treeStep.treeModel).also {
      configureTreePresentation(it)
      overrideTreeActions(it)
      addTreeListeners(it)
      Disposer.register(this) {
        treeStateHolder.saveStateFrom(it)
        it.model = null
      }
    }
    searchPatternStateFlow = MutableStateFlow(null)
    speedSearch.installSupplyTo(tree, false)

    @OptIn(FlowPreview::class)
    with(uiScope(this)) {
      launch {
        searchPatternStateFlow.drop(1).debounce(100).collectLatest { pattern ->
          applySearchPattern(pattern)
        }
      }
    }
    return tree
  }

  protected fun isChild() = parent != null

  private fun applySearchPattern(pattern: String? = speedSearch.enteredPrefix.nullize(true)) {
    if (isDisposed) return

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
          node is GitBranch && isChild() && treeStep.affectedRepositories.any { it.currentBranch == node } -> node
          node is GitBranch && !isChild() && treeStep.affectedRepositories.all { it.currentBranch == node } -> node
          node is GitBranch && treeStep.affectedRepositories.any { node in it.recentCheckoutBranches } -> node
          node is RefUnderRepository && node.repository.currentBranch == node.ref -> node
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

  internal fun restoreDefaultSize() {
    userResized = false
    WindowStateService.getInstance(project).putSizeFor(project, dimensionServiceKey, null)
    pack(true, true)
  }

  private fun installRepoListener() {
    project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      runInEdt {
        if (isDisposed) return@runInEdt

        val state = TreeState.createOn(tree)
        applySearchPattern()
        state.applyTo(tree)
      }
    })
  }

  private fun installTagsListener(treeModel: GitBranchesTreeModel) {
    project.messageBus.connect(this).subscribe(GitTagHolder.GIT_TAGS_LOADED, GitTagLoaderListener {
      runInEdt {
        val state = GitBranchesPopupTreeStateHolder.createStateForTree(tree)
        treeModel.updateTags()
        state?.applyTo(tree)
      }
    })
  }

  private fun installResizeListener() {
    addResizeListener({ userResized = true }, this)
  }

  private fun installBranchSettingsListener() {
    project.messageBus.connect(this)
      .subscribe(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED,
                 object : DvcsBranchManager.DvcsBranchManagerListener {
                   override fun branchGroupingSettingsChanged(key: GroupingKey, state: Boolean) {
                     runInEdt {
                       treeStep.setPrefixGrouping(state)
                     }
                   }

                   override fun branchFavoriteSettingsChanged() {
                     runInEdt {
                       tree.repaint()
                     }
                   }
                 })
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
    val group = am.getAction(SPEED_SEARCH_DEFAULT_ACTIONS_GROUP) as DefaultActionGroup
    for (action in group.getChildren(am)) {
      registerAction(am.getId(action),
                     KeymapUtil.getKeyStroke(action.shortcutSet),
                     createShortcutAction(action, false, mySpeedSearchPatternField.textEditor, updateSpeedSearch))
    }
  }

  private fun installShortcutActions(model: TreeModel) {
    val root = model.root
    (0 until model.getChildCount(root))
      .asSequence()
      .map { model.getChild(root, it) }
      .filterIsInstance<PopupFactoryImpl.ActionItem>()
      .map(PopupFactoryImpl.ActionItem::getAction)
      .forEach { action ->
        registerAction(am.getId(action),
                       KeymapUtil.getKeyStroke(action.shortcutSet),
                       createShortcutAction(action, true, null, null))
      }
  }

  private fun installTitleToolbar() {
    val toolbar = getHeaderToolbar() ?: return
    title.setButtonComponent(object : ActiveComponent.Adapter() {
      override fun getComponent(): JComponent = toolbar.component
    }, JBUI.Borders.emptyRight(2))
  }

  abstract fun getHeaderToolbar(): ActionToolbar?

  private fun createShortcutAction(
    action: AnAction,
    closePopup: Boolean,
    contextComponent: JComponent?,
    afterActionPerformed: (() -> Unit)?,
  ): AbstractAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (closePopup) {
        cancel()
        if (isChild()) {
          parent.cancel()
        }
      }
      val resultContext = GitBranchesTreePopupStep.createDataContext(
        project, contextComponent, treeStep.selectedRepository, treeStep.affectedRepositories)
      val actionPlace = getShortcutActionPlace()
      ActionUtil.invokeAction(action, resultContext, actionPlace, null, afterActionPerformed)
    }
  }

  protected open fun getShortcutActionPlace(): String = TOP_LEVEL_ACTION_PLACE

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    val topBorder = if (step.title.isNullOrEmpty()) JBUIScale.scale(5) else 0
    border = JBUI.Borders.empty(topBorder, JBUIScale.scale(22), 0, 0)
    background = JBUI.CurrentTheme.Popup.BACKGROUND

    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    val renderer = createRenderer(treeStep)

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
    toggleClickCount = if (Registry.`is`("git.branches.tree.popup.expand.node.on.single.click")) 1 else 2
    SmartExpander.installOn(this)
  }

  /**
   * Local branches would be expanded by [GitBranchesTreePopupStep.getPreferredSelection].
   */
  private fun JTree.calculateTopLevelVisibleRows() =
    model.getChildCount(model.root) + model.getChildCount(
      if (model is GitBranchesTreeSingleRepoModel
          && GitVcsSettings.getInstance(treeStep.project).showRecentBranches()) RecentNode
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
      if (isChild()) {
        cancel()
        true
      }
      else false
    }

    overrideBuiltInAction(TreeActions.Right.ID) {
      val path = selectionPath
      if (path != null && (path.lastPathComponent is GitRepository
                           || path.lastPathComponent is TopLevelRepository
                           || model.getChildCount(path.lastPathComponent) == 0)) {
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
      findKeyStroke == KeyStroke.getKeyStroke(e.keyCode, e.modifiersEx, e.id == KeyEvent.KEY_RELEASED) -> {
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

  final override fun afterShow() {
    selectPreferred()
    traverseNodesAndExpand()
    if (!isChild()) {
      treeStateHolder.applyStateTo(tree)
    }
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

    with(uiScope(this)) {
      launch {
        searchPatternStateFlow.emit(speedSearch.enteredPrefix.nullize(true))
      }
    }
  }

  final override fun getPreferredFocusableComponent(): JComponent = tree

  final override fun onChildSelectedFor(value: Any) {
    val path = treeStep.createTreePathFor(value) ?: return

    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  fun refresh() {
    applySearchPattern()
    mySpeedSearchPatternField.textEditor.emptyText.text = getSearchFiledEmptyText()
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
        else -> getText(value, treeStep.treeModel, treeStep.affectedRepositories) ?: ""
      }
    }
  }

  companion object {
    internal val POPUP_KEY = DataKey.create<GitBranchesTreePopupBase<*>>("GIT_BRANCHES_TREE_POPUP")
    internal val TOP_LEVEL_ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup.TopLevel.Branch.Actions")

    private const val SPEED_SEARCH_DEFAULT_ACTIONS_GROUP = "Git.Branches.Popup.SpeedSearch"

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

    private fun uiScope(parent: Disposable) =
      CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
        Disposer.register(parent) { it.cancel() }
      }
  }

  @TestOnly
  fun waitTreeExpand(timeout: Duration) {
    TreeUtil.promiseExpandAll(tree).waitForPromise(timeout)
  }

  @TestOnly
  fun getExpandedPathsSize(): Int {
    return tree.expandedPaths.size
  }

}
