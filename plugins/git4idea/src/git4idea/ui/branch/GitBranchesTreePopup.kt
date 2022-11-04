// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.*
import com.intellij.ui.popup.NextStepHandler
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultControl
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchesTreePopupStep.Companion.SPEED_SEARCH_DEFAULT_ACTIONS_GROUP
import git4idea.ui.branch.GitBranchesTreeUtil.overrideBuiltInAction
import git4idea.ui.branch.GitBranchesTreeUtil.selectFirstLeaf
import git4idea.ui.branch.GitBranchesTreeUtil.selectLastLeaf
import git4idea.ui.branch.GitBranchesTreeUtil.selectNextLeaf
import git4idea.ui.branch.GitBranchesTreeUtil.selectPrevLeaf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.event.*
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class GitBranchesTreePopup(project: Project, step: GitBranchesTreePopupStep, parent: JBPopup? = null)
  : WizardPopup(project, parent, step),
    TreePopup, NextStepHandler {

  private lateinit var tree: BranchesTree

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  private val treeStep: GitBranchesTreePopupStep
    get() = step as GitBranchesTreePopupStep

  private lateinit var searchPatternStateFlow: MutableStateFlow<String?>

  internal var userResized: Boolean
    private set

  init {
    setMinimumSize(JBDimension(300, 200))
    dimensionServiceKey = if (isChild()) null else GitBranchPopup.DIMENSION_SERVICE_KEY
    userResized = !isChild() && WindowStateService.getInstance(project).getSizeFor(project, dimensionServiceKey) != null
    installGeneralShortcutActions()
    installShortcutActions(step.treeModel)
    if (!isChild()) {
      setSpeedSearchAlwaysShown()
      installHeaderToolbar()
      installRepoListener()
      installResizeListener()
      warnThatBranchesDivergedIfNeeded()
    }
    installBranchSettingsListener()
    DataManager.registerDataProvider(component, DataProvider { dataId ->
      when {
        POPUP_KEY.`is`(dataId) -> this
        GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> treeStep.repositories
        else -> null
      }
    })
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
      addTreeMouseControlsListeners(it)
      Disposer.register(this) {
        it.model = null
      }
      val topBorder = if (step.title.isNullOrEmpty()) JBUIScale.scale(5) else 0
      it.border = JBUI.Borders.empty(topBorder, JBUIScale.scale(10), 0, 0)
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
    treeStep.setSearchPattern(pattern)
    val haveBranches = haveBranchesToShow()
    if (haveBranches) {
      selectPreferred()
    }
    super.updateSpeedSearchColors(!haveBranches)
    if (!pattern.isNullOrBlank()) {
      tree.emptyText.text = GitBundle.message("git.branches.popup.tree.no.branches", pattern)
    }
  }

  private fun haveBranchesToShow(): Boolean {
    val model = tree.model
    val root = model.root

    return (0 until model.getChildCount(root))
      .asSequence()
      .map { model.getChild(root, it) }
      .filterIsInstance<GitBranchType>()
      .any { !model.isLeaf(it) }
  }

  internal fun restoreDefaultSize() {
    userResized = false
    WindowStateService.getInstance(project).putSizeFor(project, dimensionServiceKey, null)
    pack(true, true)
  }

  private fun installRepoListener() {
    project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      runInEdt {
        applySearchPattern()
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

  private fun toggleFavorite(branch: GitBranch) {
    val branchType = GitBranchType.of(branch)
    val branchManager = project.service<GitBranchManager>()
    val anyNotFavorite = treeStep.repositories.any { repository -> !branchManager.isFavorite(branchType, repository, branch.name) }
    treeStep.repositories.forEach { repository ->
      branchManager.setFavorite(branchType, repository, branch.name, anyNotFavorite)
    }
  }

  private fun installGeneralShortcutActions() {
    registerAction("toggle_favorite", KeyStroke.getKeyStroke("SPACE"), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        (tree.lastSelectedPathComponent?.let(TreeUtil::getUserObject) as? GitBranch)?.run(::toggleFavorite)
      }
    })
  }

  private fun installSpeedSearchActions() {
    val updateSpeedSearch = {
      val textInEditor = mySpeedSearchPatternField.textEditor.text
      speedSearch.updatePattern(textInEditor)
      applySearchPattern(textInEditor)
    }
    val editorActionsContext = mapOf(PlatformCoreDataKeys.CONTEXT_COMPONENT to mySpeedSearchPatternField.textEditor)

    (ActionManager.getInstance()
      .getAction(SPEED_SEARCH_DEFAULT_ACTIONS_GROUP) as ActionGroup)
      .getChildren(null)
      .forEach { action ->
        registerAction(ActionManager.getInstance().getId(action),
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
        registerAction(ActionManager.getInstance().getId(action),
                       KeymapUtil.getKeyStroke(action.shortcutSet),
                       createShortcutAction<Any>(action))
      }
  }

  private fun installHeaderToolbar() {
    val settingsGroup = ActionManager.getInstance().getAction(GitBranchesTreePopupStep.HEADER_SETTINGS_ACTION_GROUP)
    val toolbarGroup = DefaultActionGroup(GitBranchPopupFetchAction(javaClass), settingsGroup)
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(GitBranchesTreePopupStep.ACTION_PLACE, toolbarGroup, true)
      .apply {
        targetComponent = this@GitBranchesTreePopup.component
        setReservePlaceAutoPopupIcon(false)
        component.isOpaque = false
      }
    title.setButtonComponent(object : ActiveComponent.Adapter() {
      override fun getComponent(): JComponent = toolbar.component
    }, JBUI.Borders.emptyRight(2))
  }

  private fun <T> createShortcutAction(action: AnAction,
                                       actionContext: Map<DataKey<T>, T> = emptyMap(),
                                       afterActionPerformed: (() -> Unit)? = null,
                                       closePopup: Boolean = true) = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (closePopup) {
        cancel()
      }

      val stepContext = GitBranchesTreePopupStep.createDataContext(project, treeStep.repositories)
      val resultContext =
        with(SimpleDataContext.builder().setParent(stepContext)) {
          actionContext.forEach { (key, value) -> add(key, value) }
          build()
        }

      ActionUtil.invokeAction(action, resultContext, GitBranchesTreePopupStep.ACTION_PLACE, null, afterActionPerformed)
    }
  }

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    val renderer = Renderer(treeStep)

    ClientProperty.put(this, Control.CUSTOM_CONTROL, Function { renderer.getLeftTreeIconRenderer(it) })

    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    isRootVisible = false
    showsRootHandles = true

    cellRenderer = renderer

    accessibleContext.accessibleName = GitBundle.message("git.branches.popup.tree.accessible.name")

    ClientProperty.put(this, DefaultTreeUI.LARGE_MODEL_ALLOWED, true)
    rowHeight = treeRowHeight
    isLargeModel = true
    expandsSelectedPaths = true
  }

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

    overrideBuiltInAction(TransferHandler.getPasteAction().getValue(Action.NAME) as String) {
      speedSearch.type(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
      speedSearch.update()
      true
    }
  }

  private fun addTreeMouseControlsListeners(tree: JTree) = with(tree) {
    addMouseMotionListener(SelectionMouseMotionListener())
    addMouseListener(SelectOnClickListener())
  }

  override fun getActionMap(): ActionMap = tree.actionMap

  override fun getInputMap(): InputMap = tree.inputMap

  private val selectAllKeyStroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL).shortcutSet)

  override fun process(e: KeyEvent?) {
    if (e == null) return

    val eventStroke = KeyStroke.getKeyStroke(e.keyCode, e.modifiersEx, e.id == KeyEvent.KEY_RELEASED)
    if (selectAllKeyStroke == eventStroke) {
      return
    }

    tree.processEvent(e)
  }

  override fun afterShow() {
    selectPreferred()
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
      if (!isMouseMoved(e.locationOnScreen)) return
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
    override fun mousePressed(e: MouseEvent) {
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
      if (point != null && userObject is GitBranch && isMainIconAt(point, userObject)) {
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
                          .filter { ClientProperty.get(it, Renderer.MAIN_ICON) == true }
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
      if (nextStep is GitBranchesTreePopupStep) GitBranchesTreePopup(project, nextStep, this)
      else createPopup(this, nextStep, parentValue)

    myChild.show(content, content.locationOnScreen.x + content.width - STEP_X_PADDING, point.y, true)
  }

  override fun onSpeedSearchPatternChanged() {
    with(uiScope(this)) {
      launch {
        searchPatternStateFlow.emit(speedSearch.enteredPrefix.nullize(true))
      }
    }
  }

  override fun getPreferredFocusableComponent(): JComponent = tree

  override fun onChildSelectedFor(value: Any) {
    val path = value as? TreePath ?: return
    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  companion object {

    internal val POPUP_KEY = DataKey.create<GitBranchesTreePopup>("GIT_BRANCHES_TREE_POPUP")

    private val treeRowHeight = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.List.rowHeight() else JBUIScale.scale(22)

    @JvmStatic
    fun isEnabled() = Registry.`is`("git.branches.popup.tree", false)
                      && !ExperimentalUI.isNewUI()

    @JvmStatic
    fun show(project: Project) {
      create(project).showCenteredInCurrentWindow(project)
    }

    @JvmStatic
    fun create(project: Project): JBPopup {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      return GitBranchesTreePopup(project, GitBranchesTreePopupStep(project, repositories, true))
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

    private class BranchesTree(model: TreeModel): Tree(model) {

      init {
        background = JBUI.CurrentTheme.Popup.BACKGROUND
      }

      //Change visibility of processEvent to be able to delegate key events dispatched in WizardPopup directly to tree
      //This will allow to handle events like "copy-paste" in AbstractPopup.speedSearch
      public override fun processEvent(e: AWTEvent?) {
        e?.source = this
        super.processEvent(e)
      }
    }

    private class Renderer(private val step: GitBranchesTreePopupStep) : TreeCellRenderer {

      fun getLeftTreeIconRenderer(path: TreePath): Control? {
        val lastComponent = path.lastPathComponent
        val defaultIcon = step.getNodeIcon(lastComponent, false) ?: return null
        val selectedIcon = step.getNodeIcon(lastComponent, true) ?: return null

        return DefaultControl(defaultIcon, defaultIcon, selectedIcon, selectedIcon)
      }

      private val mainIconComponent = JLabel().apply {
        ClientProperty.put(this, MAIN_ICON, true)
        border = JBUI.Borders.emptyRight(4)  // 6 px in spec, but label width is differed
      }
      private val mainTextComponent = SimpleColoredComponent().apply {
        isOpaque = false
        border = JBUI.Borders.empty()
      }
      private val secondaryLabel = JLabel().apply {
        border = JBUI.Borders.emptyLeft(10)
        horizontalAlignment = SwingConstants.RIGHT
      }
      private val arrowLabel = JLabel().apply {
        border = JBUI.Borders.emptyLeft(4) // 6 px in spec, but label width is differed
      }
      private val incomingOutgoingLabel = JLabel().apply {
        border = JBUI.Borders.emptyLeft(10)
      }

      private val textPanel = JBUI.Panels.simplePanel()
        .addToLeft(JBUI.Panels.simplePanel(mainTextComponent).addToLeft(mainIconComponent).andTransparent())
        .addToCenter(JBUI.Panels.simplePanel(incomingOutgoingLabel).addToRight(secondaryLabel).andTransparent())
        .andTransparent()

      private val mainPanel = JBUI.Panels.simplePanel()
        .addToCenter(textPanel)
        .addToRight(arrowLabel)
        .andTransparent()
        .withBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.cellPadding().right))

      override fun getTreeCellRendererComponent(tree: JTree?,
                                                value: Any?,
                                                selected: Boolean,
                                                expanded: Boolean,
                                                leaf: Boolean,
                                                row: Int,
                                                hasFocus: Boolean): Component {
        val userObject = TreeUtil.getUserObject(value)

        mainIconComponent.apply {
          icon = step.getIcon(userObject, selected)
          isVisible = icon != null
        }

        mainTextComponent.apply {
          background = JBUI.CurrentTheme.Tree.background(selected, true)
          foreground = JBUI.CurrentTheme.Tree.foreground(selected, true)

          clear()
          append(step.getText(userObject).orEmpty())
        }

        val (inOutIcon, inOutTooltip) = step.getIncomingOutgoingIconWithTooltip(userObject)
        tree?.toolTipText = inOutTooltip

        incomingOutgoingLabel.apply {
          icon = inOutIcon
          isVisible = icon != null
        }

        arrowLabel.apply {
          isVisible = step.hasSubstep(userObject)
          icon = if (selected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
        }

        secondaryLabel.apply {
          text = step.getSecondaryText(userObject)
          //todo: LAF color
          foreground = if (selected) JBUI.CurrentTheme.Tree.foreground(true, true) else JBColor.GRAY

          border = if (!arrowLabel.isVisible && ExperimentalUI.isNewUI()) {
            JBUI.Borders.empty(0, 10, 0, JBUI.CurrentTheme.Popup.Selection.innerInsets().right)
          } else {
            JBUI.Borders.emptyLeft(10)
          }
        }

        if (tree != null && value != null) {
          SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, mainTextComponent, true, selected)
        }

        return mainPanel
      }

      companion object {
        @JvmField
        internal val MAIN_ICON = Key.create<Boolean>("MAIN_ICON")
      }
    }
  }
}
