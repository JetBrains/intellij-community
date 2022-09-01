// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.ui.ActiveComponent
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.TreeActions
import com.intellij.ui.popup.NextStepHandler
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.FontUtil
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
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
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.*
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class GitBranchesTreePopup(project: Project, step: GitBranchesTreePopupStep)
  : WizardPopup(project, null, step),
    TreePopup, NextStepHandler {

  private lateinit var tree: JTree

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  private val treeStep: GitBranchesTreePopupStep
    get() = step as GitBranchesTreePopupStep

  private lateinit var searchPatternStateFlow: MutableStateFlow<String?>

  private var userResized: Boolean

  init {
    setMinimumSize(JBDimension(200, 200))
    dimensionServiceKey = GitBranchPopup.DIMENSION_SERVICE_KEY
    userResized = WindowStateService.getInstance(project).getSizeFor(project, dimensionServiceKey) != null
    setSpeedSearchAlwaysShown()
    installShortcutActions(step.treeModel)
    installHeaderToolbar()
    installResizeListener()
    DataManager.registerDataProvider(component, DataProvider { dataId -> if (POPUP_KEY.`is`(dataId)) this else null })
  }

  override fun createContent(): JComponent {
    tree = Tree(treeStep.treeModel).also {
      configureTreePresentation(it)
      overrideTreeActions(it)
      addTreeMouseControlsListeners(it)
      Disposer.register(this) {
        it.model = null
      }
    }
    searchPatternStateFlow = MutableStateFlow(null)
    speedSearch.installSupplyTo(tree, false)

    @OptIn(FlowPreview::class)
    with(uiScope(this)) {
      launch {
        searchPatternStateFlow.drop(1).debounce(100).collectLatest { pattern ->
          treeStep.setSearchPattern(pattern)
          selectPreferred()
        }
      }
    }
    return tree
  }

  internal fun restoreDefaultSize() {
    userResized = false
    WindowStateService.getInstance(project).putSizeFor(project, dimensionServiceKey, null)
    pack(true, true)
  }

  private fun installResizeListener() {
    val popupWindow = popupWindow ?: return
    val windowListener: ComponentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        userResized = true
      }
    }
    popupWindow.addComponentListener(windowListener)
    addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        popupWindow.removeComponentListener(windowListener)
      }
    })
  }

  override fun storeDimensionSize() {
    if (userResized) {
      super.storeDimensionSize()
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
        registerAction(ActionManager.getInstance().getId(action), KeymapUtil.getKeyStroke(action.shortcutSet), createShortcutAction(action))
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

  private fun createShortcutAction(action: AnAction) = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      cancel()
      ActionUtil.invokeAction(action,
                              GitBranchesTreePopupStep.createDataContext(project, treeStep.repository),
                              GitBranchesTreePopupStep.ACTION_PLACE, null, null)
    }
  }

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    ClientProperty.put(this, RenderingUtil.SEPARATOR_ABOVE_PREDICATE, Predicate { treeStep.isSeparatorAboveRequired(it) })

    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    isRootVisible = false
    showsRootHandles = true

    cellRenderer = Renderer(treeStep)

    accessibleContext.accessibleName = GitBundle.message("git.branches.popup.tree.accessible.name")

    ClientProperty.put(this, DefaultTreeUI.LARGE_MODEL_ALLOWED, true)
    rowHeight = JBUIScale.scale(20)
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

    overrideBuiltInAction(TreeActions.Right.ID) {
      val path = selectionPath
      if (path != null && model.getChildCount(path.lastPathComponent) == 0) {
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

  private fun addTreeMouseControlsListeners(tree: JTree) = with(tree) {
    addMouseMotionListener(SelectionMouseMotionListener())
    addMouseListener(SelectOnClickListener())
  }

  override fun getActionMap(): ActionMap = tree.actionMap

  override fun getInputMap(): InputMap = tree.inputMap

  override fun afterShow() {
    selectPreferred()
  }

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

    myChild = createPopup(this, nextStep, parentValue)
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
    val path = value as TreePath
    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  companion object {

    internal val POPUP_KEY = DataKey.create<GitBranchesTreePopup>("GIT_BRANCHES_TREE_POPUP")

    @JvmStatic
    fun show(project: Project, repository: GitRepository) {
      GitBranchesTreePopup(project, GitBranchesTreePopupStep(project, repository)).showCenteredInCurrentWindow(project)
    }

    private fun uiScope(parent: Disposable) =
      CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
        Disposer.register(parent) { it.cancel() }
      }

    private class Renderer(private val step: GitBranchesTreePopupStep) : TreeCellRenderer {

      private val mainLabel = JLabel().apply {
        border = JBUI.Borders.emptyBottom(1)
      }
      private val secondaryLabel = JLabel().apply {
        font = FontUtil.minusOne(font)
        border = JBUI.Borders.empty(0, 10, 1, 5)
        horizontalAlignment = SwingConstants.RIGHT
      }
      private val arrowLabel = JLabel().apply {
        border = JBUI.Borders.empty(0, 2)
      }

      private val textPanel = JBUI.Panels.simplePanel()
        .addToLeft(mainLabel)
        .addToCenter(secondaryLabel)
        .andTransparent()

      private val mainPanel = JBUI.Panels.simplePanel()
        .addToCenter(textPanel)
        .addToRight(arrowLabel)
        .andTransparent()

      override fun getTreeCellRendererComponent(tree: JTree?,
                                                value: Any?,
                                                selected: Boolean,
                                                expanded: Boolean,
                                                leaf: Boolean,
                                                row: Int,
                                                hasFocus: Boolean): Component {
        val userObject = TreeUtil.getUserObject(value)
        mainLabel.apply {
          icon = step.getIcon(userObject)
          text = step.getText(userObject)
          foreground = JBUI.CurrentTheme.Tree.foreground(selected, true)
        }

        secondaryLabel.apply {
          text = step.getSecondaryText(userObject)
          //todo: LAF color
          foreground = if(selected) JBUI.CurrentTheme.Tree.foreground(true, true) else JBColor.GRAY
        }

        arrowLabel.apply {
          isVisible = step.hasSubstep(userObject)
          icon = if (selected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
        }
        return mainPanel
      }
    }
  }
}
