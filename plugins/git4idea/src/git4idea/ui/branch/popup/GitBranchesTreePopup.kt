// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.*
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.TreeState
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.*
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
import git4idea.GitVcs
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitBranchPopupFetchAction
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.SINGLE_REPOSITORY_ACTION_PLACE
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.SPEED_SEARCH_DEFAULT_ACTIONS_GROUP
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.TOP_LEVEL_ACTION_PLACE
import git4idea.ui.branch.popup.compose.createComposeBranchesPopup
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.branch.tree.GitBranchesTreeModel.BranchTypeUnderRepository
import git4idea.ui.branch.tree.GitBranchesTreeModel.BranchUnderRepository
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import git4idea.ui.branch.tree.GitBranchesTreeRenderer.Companion.getText
import git4idea.ui.branch.tree.GitBranchesTreeSingleRepoModel
import git4idea.ui.branch.tree.GitBranchesTreeUtil.overrideBuiltInAction
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectFirstLeaf
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectLastLeaf
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectNextLeaf
import git4idea.ui.branch.tree.GitBranchesTreeUtil.selectPrevLeaf
import git4idea.ui.branch.tree.recentCheckoutBranches
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
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

class GitBranchesTreePopup(project: Project, step: GitBranchesTreePopupStep, parent: JBPopup? = null, parentValue: Any? = null)
  : WizardPopup(project, parent, step),
    TreePopup, NextStepHandler {

  private lateinit var tree: Tree

  private val treeStateHolder = project.service<GitBranchesPopupTreeStateHolder>()

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  private val treeStep: GitBranchesTreePopupStep
    get() = step as GitBranchesTreePopupStep

  private lateinit var searchPatternStateFlow: MutableStateFlow<String?>

  internal var userResized: Boolean
    private set

  private val expandedPaths = HashSet<TreePath>()

  init {
    setParentValue(parentValue)
    minimumSize = if (isNewUI) JBDimension(350, 300) else JBDimension(300, 200)
    dimensionServiceKey = if (isChild()) null else DIMENSION_SERVICE_KEY
    userResized = !isChild() && WindowStateService.getInstance(project).getSizeFor(project, dimensionServiceKey) != null
    installGeneralShortcutActions()
    installShortcutActions(step.treeModel)
    if (!isChild()) {
      setSpeedSearchAlwaysShown()
      installHeaderToolbar()
      installRepoListener()
      installResizeListener()
      warnThatBranchesDivergedIfNeeded()
      DvcsBranchSyncPolicyUpdateNotifier(project, GitVcs.getInstance(project),
                                         GitVcsSettings.getInstance(project), GitRepositoryManager.getInstance(project))
        .initBranchSyncPolicyIfNotInitialized()
    }
    installBranchSettingsListener()
    DataManager.registerDataProvider(component, DataProvider { dataId ->
      when {
        POPUP_KEY.`is`(dataId) -> this
        GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> treeStep.affectedRepositories
        else -> null
      }
    })
  }

  override fun setHeaderComponent(c: JComponent?) {
    mySpeedSearchPatternField.textEditor.apply {
      emptyText.text = ApplicationBundle.message("editorsearch.search.hint")
      FragmentedSettingsUtil.setupPlaceholderVisibility(mySpeedSearchPatternField.textEditor)
    }

    if (!isNewUI) {
      super.setHeaderComponent(c)
      return
    }

    val searchBorder =  mySpeedSearchPatternField.border
    mySpeedSearchPatternField.border = null

    val toolbar = createToolbar().component.apply {
      border = JBUI.Borders.emptyLeft(6)
    }
    val panel = BorderLayoutPanel()
      .addToCenter(mySpeedSearchPatternField)
      .addToRight(toolbar).apply {
        border = searchBorder
        background = JBUI.CurrentTheme.Popup.BACKGROUND
      }

    super.setHeaderComponent(panel)
  }

  private fun warnThatBranchesDivergedIfNeeded() {
    if (treeStep.isBranchesDiverged()) {
      setWarning(DvcsBundle.message("branch.popup.warning.branches.have.diverged"))
    }
  }

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

  private fun isChild() = parent != null

  private fun applySearchPattern(pattern: String? = speedSearch.enteredPrefix.nullize(true)) {
    if (isDisposed) return

    treeStep.updateTreeModelIfNeeded(tree, pattern)
    treeStep.setSearchPattern(pattern)
    val haveBranches = traverseNodesAndExpand()
    if (haveBranches) {
      selectPreferred()
      expandPreviouslyExpandedBranches()
    }
    val model = tree.model
    super.updateSpeedSearchColors(model.getChildCount(model.root) == 0)
    if (!pattern.isNullOrBlank()) {
      tree.emptyText.text = GitBundle.message("git.branches.popup.tree.no.branches", pattern)
    }
  }

  private fun expandPreviouslyExpandedBranches() {
    expandedPaths.toSet().forEach { path -> TreeUtil.promiseExpand(tree, path) }
  }

  private fun traverseNodesAndExpand(): Boolean {
    val model = tree.model
    var haveBranches = false

    TreeUtil.treeTraverser(tree)
      .filter(BranchType::class or BranchTypeUnderRepository::class or GitBranch::class or BranchUnderRepository::class)
      .forEach { node ->
        if (!haveBranches && !model.isLeaf(node)) {
          haveBranches = true
        }

        val nodeToExpand = when {
          node is GitBranch && isChild() && treeStep.affectedRepositories.any { it.currentBranch == node } -> node
          node is GitBranch && !isChild() && treeStep.affectedRepositories.all { it.currentBranch == node } -> node
          node is GitBranch && treeStep.affectedRepositories.any { node in it.recentCheckoutBranches } -> node
          node is BranchUnderRepository && node.repository.currentBranch == node.branch -> node
          node is BranchTypeUnderRepository -> node
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

  override fun storeDimensionSize() {
    if (userResized) {
      super.storeDimensionSize()
    }
  }

  private fun toggleFavorite(userObject: Any?) {
    val branchUnderRepository = userObject as? BranchUnderRepository
    val branch = userObject as? GitBranch ?: branchUnderRepository?.branch ?: return
    val repositories = branchUnderRepository?.repository?.let(::listOf) ?: treeStep.affectedRepositories
    val branchType = GitBranchType.of(branch)
    val branchManager = project.service<GitBranchManager>()
    val anyNotFavorite = repositories.any { repository -> !branchManager.isFavorite(branchType, repository, branch.name) }
    repositories.forEach { repository ->
      branchManager.setFavorite(branchType, repository, branch.name, anyNotFavorite)
    }
  }

  private fun installGeneralShortcutActions() {
    registerAction("toggle_favorite", KeyStroke.getKeyStroke("SPACE"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        tree.lastSelectedPathComponent?.let(TreeUtil::getUserObject).run(::toggleFavorite)
      }
    })
  }

  private fun installSpeedSearchActions() {
    val updateSpeedSearch = {
      val textInEditor = mySpeedSearchPatternField.textEditor.text
      speedSearch.updatePattern(textInEditor)
      onSpeedSearchPatternChanged()
    }
    val editorActionsContext = mapOf(PlatformCoreDataKeys.CONTEXT_COMPONENT to mySpeedSearchPatternField.textEditor)

    (am.getAction(SPEED_SEARCH_DEFAULT_ACTIONS_GROUP) as ActionGroup)
      .getChildren(null)
      .forEach { action ->
        registerAction(am.getId(action),
                       KeymapUtil.getKeyStroke(action.shortcutSet),
                       createShortcutAction(action, editorActionsContext, updateSpeedSearch, false))
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
                       createShortcutAction<Any>(action))
      }
  }

  private fun installHeaderToolbar() {
    if (isNewUI) return
    val toolbar = createToolbar()
    title.setButtonComponent(object : ActiveComponent.Adapter() {
      override fun getComponent(): JComponent = toolbar.component
    }, JBUI.Borders.emptyRight(2))
  }

  private fun createToolbar(): ActionToolbar {
    val settingsGroup = am.getAction(GitBranchesTreePopupStep.HEADER_SETTINGS_ACTION_GROUP)
    val toolbarGroup = DefaultActionGroup(GitBranchPopupFetchAction(javaClass), settingsGroup)
    return am.createActionToolbar(TOP_LEVEL_ACTION_PLACE, toolbarGroup, true)
      .apply {
        targetComponent = component
        setReservePlaceAutoPopupIcon(false)
        component.isOpaque = false
        DataManager.registerDataProvider(component, DataProvider { dataId ->
          when {
            POPUP_KEY.`is`(dataId) -> this@GitBranchesTreePopup
            GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> treeStep.repositories
            else -> null
          }
        })
      }
  }

  private fun <T> createShortcutAction(action: AnAction,
                                       actionContext: Map<DataKey<T>, T> = emptyMap(),
                                       afterActionPerformed: (() -> Unit)? = null,
                                       closePopup: Boolean = true) = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (closePopup) {
        cancel()
        if (isChild()) {
          parent.cancel()
        }
      }

      val stepContext = GitBranchesTreePopupStep.createDataContext(project, treeStep.selectedRepository, treeStep.affectedRepositories)
      val resultContext =
        with(SimpleDataContext.builder().setParent(stepContext)) {
          actionContext.forEach { (key, value) -> add(key, value) }
          build()
        }

      val actionPlace = if (isChild()) SINGLE_REPOSITORY_ACTION_PLACE else TOP_LEVEL_ACTION_PLACE
      ActionUtil.invokeAction(action, resultContext, actionPlace, null, afterActionPerformed)
    }
  }

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    val topBorder = if (step.title.isNullOrEmpty()) JBUIScale.scale(5) else 0
    border = JBUI.Borders.empty(topBorder, JBUIScale.scale(22), 0, 0)
    background = JBUI.CurrentTheme.Popup.BACKGROUND

    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    val renderer = GitBranchesTreePopupRenderer(treeStep)

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
      if (model is GitBranchesTreeSingleRepoModel) GitBranchesTreeModel.RecentNode else GitBranchType.LOCAL)

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
      if (path != null && (path.lastPathComponent is GitRepository || model.getChildCount(path.lastPathComponent) == 0)) {
        handleSelect(false, null)
        true
      }
      else false
    }

    overrideBuiltInAction(TreeActions.Down.ID) {
      if (speedSearch.isHoldingFilter) selectNextLeaf()
      else false
    }

    overrideBuiltInAction(TreeActions.Up.ID) {
      if (speedSearch.isHoldingFilter) selectPrevLeaf()
      else false
    }

    overrideBuiltInAction(TreeActions.Home.ID) {
      if (speedSearch.isHoldingFilter) selectFirstLeaf()
      else false
    }

    overrideBuiltInAction(TreeActions.End.ID) {
      if (speedSearch.isHoldingFilter) selectLastLeaf()
      else false
    }
  }

  override fun processKeyEvent(e: KeyEvent) {
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

  override fun getActionMap(): ActionMap = tree.actionMap

  override fun getInputMap(): InputMap = tree.inputMap

  private val findKeyStroke = KeymapUtil.getKeyStroke(am.getAction("Find").shortcutSet)

  override fun afterShow() {
    selectPreferred()
    traverseNodesAndExpand()
    if (!isChild()) {
      treeStateHolder.applyStateTo(tree)
    }
    if (treeStep.isSpeedSearchEnabled) {
      installSpeedSearchActions()
    }
  }

  override fun updateSpeedSearchColors(error: Boolean) {} // update colors only after branches tree model update

  private fun selectPreferred() {
    val preferredSelection = treeStep.getPreferredSelection()
    if (preferredSelection != null) {
      tree.makeVisible(preferredSelection)
      tree.selectionPath = preferredSelection
      TreeUtil.scrollToVisible(tree, preferredSelection, true)
    }
    else TreeUtil.promiseSelectFirstLeaf(tree)
  }

  override fun isResizable(): Boolean = true

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
      val branchUnderRepository = userObject as? BranchUnderRepository
      val branch = userObject as? GitBranch ?: branchUnderRepository?.branch
      if (point != null && branch != null && isMainIconAt(point, branch)) {
        toggleFavorite(userObject)
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

  override fun handleNextStep(nextStep: PopupStep<*>?, parentValue: Any) {
    val selectionPath = tree.selectionPath ?: return
    val pathBounds = tree.getPathBounds(selectionPath) ?: return
    val point = Point(pathBounds.x, pathBounds.y)
    SwingUtilities.convertPointToScreen(point, tree)

    myChild =
      if (nextStep is GitBranchesTreePopupStep) GitBranchesTreePopup(project, nextStep, this, parentValue)
      else createPopup(this, nextStep, parentValue)

    myChild.show(content, content.locationOnScreen.x + content.width - STEP_X_PADDING, point.y, true)
  }

  override fun onSpeedSearchPatternChanged() {
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

  override fun getPreferredFocusableComponent(): JComponent = tree

  override fun onChildSelectedFor(value: Any) {
    val path = treeStep.createTreePathFor(value) ?: return

    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  override fun createWarning(text: String): JComponent {
    return DvcsBranchesDivergedBanner.create("reference.VersionControl.Git.SynchronousBranchControl", text)
  }

  fun refresh() {
    applySearchPattern()
  }

  private inner class BranchesTree(model: TreeModel) : Tree(model) {

    override fun convertValueToText(value: Any?,
                                    selected: Boolean,
                                    expanded: Boolean,
                                    leaf: Boolean,
                                    row: Int,
                                    hasFocus: Boolean): String {
      return when (value) {
        null -> ""
        is ItemPresentation -> value.presentableText.orEmpty()
        is GitBranch -> value.name
        else -> getText(value, treeStep.treeModel, treeStep.affectedRepositories) ?: ""
      }
    }
  }

  private val am
    get() = ActionManager.getInstance()

  companion object {

    private const val DIMENSION_SERVICE_KEY = "Git.Branch.Popup"

    private inline val isNewUI
      get() = ExperimentalUI.isNewUI()

    internal val POPUP_KEY = DataKey.create<GitBranchesTreePopup>("GIT_BRANCHES_TREE_POPUP")

    internal val treeRowHeight: Int
      get() = if (isNewUI) JBUI.CurrentTheme.List.rowHeight() else JBUIScale.scale(22)

    /**
     * @param selectedRepository - Selected repository:
     * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
     */
    @JvmStatic
    fun show(project: Project, selectedRepository: GitRepository?) {
      create(project, selectedRepository).showCenteredInCurrentWindow(project)
    }

    /**
     * @param selectedRepository - Selected repository:
     * e.g. [git4idea.branch.GitBranchUtil.guessRepositoryForOperation] or [git4idea.branch.GitBranchUtil.guessWidgetRepository]
     */
    @JvmStatic
    fun create(project: Project, selectedRepository: GitRepository?): JBPopup {
      val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)
      val selectedRepoIfNeeded = if (GitBranchActionsUtil.userWantsSyncControl(project)) null else selectedRepository
      if (Registry.`is`("git.experimental.compose.branches.popup")) {
        return createComposeBranchesPopup(project, selectedRepoIfNeeded ?: repositories.first())
      }
      return GitBranchesTreePopup(project, GitBranchesTreePopupStep(project, selectedRepoIfNeeded, repositories, true))
    }

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
}
