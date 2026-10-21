// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeTooltipManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.unscaledProjectIconSize
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.status.ProcessPopup
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase
import com.intellij.openapi.wm.impl.welcomeScreen.statistics.RecentProjectsFusEventFields
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.FilteringTree
import com.intellij.ui.PopupHandler
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SmartExpander
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UpdateScaleHelper
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

@ApiStatus.Internal
class RecentProjectFilteringTree internal constructor(
  treeComponent: Tree,
  parentDisposable: Disposable,
  collectors: List<() -> List<RecentProjectTreeItem>>,
  val disableSearchFieldBorder: Boolean,
  projectsProgress: StateFlow<ProjectsProgress>,
  getProgressFor: (RecentProjectTreeItem) -> Flow<List<RecentProjectProgress>>,
  onCancel: (ProgressId) -> Unit,
) : FilteringTree<DefaultMutableTreeNode, RecentProjectTreeItem>(treeComponent, DefaultMutableTreeNode(RootItem(collectors))) {
  init {
    val projectActionButtonViewModel = ProjectActionButtonViewModel()
    val filePathChecker = createFilePathChecker()
    Disposer.register(parentDisposable, filePathChecker)

    // Provide data context for projects actions
    DataManager.registerDataProvider(treeComponent) { dataId ->
      when {
        RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY.`is`(dataId) -> getSelectedItem(tree)
        RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEMS_KEY.`is`(dataId) -> getSelectedItems(tree)
        RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_TREE_KEY.`is`(dataId) -> tree
        else -> null
      }
    }

    treeComponent.addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItems(treeComponent) }

    val mouseListener =
      ProjectActionMouseListener(treeComponent, projectActionButtonViewModel, filePathChecker::isValid, projectsProgress, getProgressFor,
                                 onCancel)
    Disposer.register(parentDisposable, mouseListener)
    treeComponent.addMouseListener(mouseListener)
    treeComponent.addMouseMotionListener(mouseListener)
    treeComponent.addTreeWillExpandListener(ToggleStateListener())

    treeComponent.putClientProperty(Control.Painter.KEY, Control.Painter.LEAF_WITHOUT_INDENT)
    treeComponent.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    treeComponent.putClientProperty(
      RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
      Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), isSelected = true, hasFocus = true) }
    )

    SmartExpander.installOn(treeComponent)

    treeComponent.isRootVisible = false
    treeComponent.cellRenderer = ProjectActionRenderer(filePathChecker::isValid, projectActionButtonViewModel, projectsProgress)
    treeComponent.rowHeight = 0 // Fix tree renderer size on macOS
    treeComponent.toggleClickCount = 0

    treeComponent.setUI(FullRendererComponentTreeUI())
    treeComponent.setExpandableItemsEnabled(false)

    treeComponent.addMouseMotionListener(MouseHoverListener(treeComponent))

    treeComponent.accessibleContext.accessibleName = IdeBundle.message("welcome.screen.recent.projects.accessible.name")

    searchModel.updateStructure()
  }

  /**
   * Collects the projects again and rebuilds the tree.
   */
  fun updateTree() {
    rootItem.invalidate()
    searchModel.updateStructure()
    expandGroups()
  }

  override fun getNodeClass() = DefaultMutableTreeNode::class.java

  override fun getText(item: RecentProjectTreeItem?): String = when (item) {
    is RecentProjectItem -> item.searchName()
    is ProviderRecentProjectItem -> item.searchName()
    else -> item?.displayName().orEmpty()
  }

  override fun getChildren(item: RecentProjectTreeItem): Iterable<RecentProjectTreeItem> = item.children()

  override fun createNode(item: RecentProjectTreeItem): DefaultMutableTreeNode = DefaultMutableTreeNode(item)

  override fun installSearchField(): SearchTextField {
    return super.installSearchField().apply {
      isOpaque = false
      border = JBUI.Borders.empty()

      textEditor.apply {
        isOpaque = false
        if (disableSearchFieldBorder) {
          border = JBUI.Borders.empty()
        }
        emptyText.text = IdeBundle.message("welcome.screen.search.projects.empty.text")
        accessibleContext.accessibleName = IdeBundle.message("welcome.screen.search.projects.empty.text")
        TextComponentEmptyText.setupPlaceholderVisibility(this)

        addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItems(tree) }
        addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.ALT_DOWN_MASK)) { removeItem(tree) }
      }
    }
  }

  override fun expandTreeOnSearchUpdateComplete(pattern: String?) {
    TreeUtil.expandAll(tree)
  }

  // The speed search restores the selection after the filter, also for the nodes that the filter removed from the tree.
  override fun onSpeedSearchUpdateComplete(pattern: String?) {
    val removedPaths = tree.selectionPaths?.filter { tree.getRowForPath(it) < 0 } ?: return
    tree.selectionModel.removeSelectionPaths(removedPaths.toTypedArray())
  }

  override fun useIdentityHashing(): Boolean = false

  private val rootItem: RootItem
    get() = searchModel.rootObject as RootItem

  /**
   * Creates the checker for the paths of the recent projects in the tree.
   * The checker and the first structure update share one collection of the projects.
   */
  private fun createFilePathChecker(): RecentProjectPanel.FilePathChecker {
    val recentProjects = mutableListOf<RecentProjectItem>()
    for (item in rootItem.children()) {
      when (item) {
        is RecentProjectItem -> recentProjects.add(item)
        is ProjectsGroupItem -> recentProjects.addAll(item.children)
        else -> {}
      }
    }

    val treeUpdater = Runnable {
      searchModel.updateStructure()
      tree.repaint()
    }

    return RecentProjectPanel.FilePathChecker(treeUpdater, recentProjects.map { it.projectPath })
  }

  internal fun expandGroups() {
    for (child in root.children()) {
      val treeNode = child as DefaultMutableTreeNode
      val item = treeNode.userObject
      if (item is ProjectsGroupItem) {
        val treePath = TreePath(child.path)
        if (item.group.isExpanded)
          tree.expandPath(treePath)
        else
          tree.collapsePath(treePath)
      }
    }
  }

  /**
   * @return true if the last opened project was selected
   */
  fun selectLastOpenedProject(): Boolean {
    val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
    val projectPath = recentProjectsManager.getLastOpenedProject() ?: return false

    val node = TreeUtil.findNode(root, Condition {
      when (val item = TreeUtil.getUserObject(RecentProjectTreeItem::class.java, it)) {
        is RecentProjectItem -> item.projectPath == projectPath
        is CloneableProjectItem -> item.projectPath.invariantSeparatorsPathString == projectPath
        else -> false
      }
    })

    if (node != null) {
      TreeUtil.selectNode(tree, node)
      return true
    }
    return false
  }

  @ApiStatus.Internal
  fun selectLastOpenedProjectOrTheFirstInTree(): Boolean {
    if (selectLastOpenedProject()) {
      return true
    }

    if (root.childCount <= 0) {
      return false
    }
    val firstChild = root.firstChild
    if (firstChild != null) {
      TreeUtil.selectNode(tree, firstChild)
      return true
    }
    return false
  }

  private class ProjectActionMouseListener(
    private val tree: Tree,
    private val projectActionButtonViewModel: ProjectActionButtonViewModel,
    private val isProjectPathValid: (String) -> Boolean,
    private val projectsProgress: StateFlow<ProjectsProgress>,
    private val getProgressFor: (RecentProjectTreeItem) -> Flow<List<RecentProjectProgress>>,
    private val onCancel: (ProgressId) -> Unit,
  ) : PopupHandler(), Disposable {
    private val buttonPopups = mutableMapOf<String, ActionPopupMenu>()
    private var progressPopup: RecentProjectTaskProgressPopup? = null

    /** Closes the process window with the tree. A host can dispose the tree off the EDT, and hiding the window needs the EDT. */
    override fun dispose() {
      runInEdt(ModalityState.any()) {
        progressPopup?.close()
        progressPopup = null
      }
    }

    override fun mouseMoved(mouseEvent: MouseEvent) {
      if (actionIsInProgress(mouseEvent)) return
      updateHoverAndSelection(mouseEvent.point)
    }

    /**
     * Selects the row under [point] and sets the button hover to match, then repaints the row. A null
     * [point] means the mouse is off the tree, so it drops the selection and the hover. The mouse move
     * handler and the popup close handler share this, so the button hover always follows the cursor.
     */
    private fun updateHoverAndSelection(point: Point?) {
      val row = if (point != null) TreeUtil.getRowForLocation(tree, point.x, point.y) else -1

      var repaintRow = false
      if (row != -1) {
        if (!tree.isRowSelected(row)) {
          tree.setSelectionRow(row)
          repaintRow = true
        }
      }
      else {
        tree.clearSelection()
      }

      // Repaint the row when the hovered button changes, otherwise the renderer keeps the old hover
      // state and the button never highlights while the mouse stays in the same row.
      val hovered = rowButtonAt(point)
      if (projectActionButtonViewModel.hovered != hovered) {
        projectActionButtonViewModel.hovered = hovered
        repaintRow = true
      }
      projectActionButtonViewModel.hoveredSpinnerId =
        itemAt(row)?.takeIf { hovered is RecentProjectRowButton.TaskProgress }?.let(::getRowId)

      if (repaintRow && row != -1) {
        // Repaint whole row to avoid flickering of row buttons
        tree.repaint(tree.getRowBounds(row))
      }
    }

    private fun toggleProgressPopup(row: Int) {
      val item = itemAt(row) ?: return
      val rowId = getRowId(item) ?: return
      val bounds = getSpinnerBounds(row) ?: return

      val isShowingRow = progressPopup?.isShowingRow(rowId) == true
      progressPopup?.close()
      if (isShowingRow) return
      progressPopup = RecentProjectTaskProgressPopup(
        rowId = rowId,
        windowTitle = IdeBundle.message("welcome.screen.recent.project.processes.window.title", item.displayName()),
        operations = getProgressFor(item),
        onCancel = onCancel,
        onClose = {
          projectActionButtonViewModel.windowRowId = null
          // The spinner that goes away can be on a row the cursor has left, and no hover repaints that row.
          tree.repaint()
        },
      ).also { it.show(RelativePoint(tree, Point(bounds.x, bounds.y + bounds.height))) }
      projectActionButtonViewModel.windowRowId = rowId
    }

    private fun getSpinnerBounds(row: Int): Rectangle? {
      val buttons = rowButtonsAt(row)
      val index = buttons.indexOfFirst { it is RecentProjectRowButton.TaskProgress }
      return if (index < 0) null else buttonRect(row, buttons, index)
    }

    private fun actionIsInProgress(mouseEvent: MouseEvent): Boolean {
      return buttonPopups.values.any { it.component.isVisible } ||
             mouseEvent.isMultipleSelectionInProgress
    }

    override fun mouseExited(e: MouseEvent?) {
      val mouseEvent = e ?: return
      if (actionIsInProgress(mouseEvent)) return

      updateHoverAndSelection(null)
    }

    override fun mouseReleased(mouseEvent: MouseEvent) {
      super.mouseReleased(mouseEvent)
      if (mouseEvent.isConsumed || mouseEvent.isMultipleSelectionInProgress) {
        return
      }

      val point = mouseEvent.point
      val treePath = TreeUtil.getPathForLocation(tree, point.x, point.y) ?: return
      val item = TreeUtil.getLastUserObject(RecentProjectTreeItem::class.java, treePath) ?: return

      // Avoid double-clicking an arrow button
      if (item is ProjectsGroupItem && TreeUtil.isLocationInExpandControl(tree, point.x, point.y)) {
        return
      }

      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        val button = rowButtonAt(point)
        if (button == null) {
          activateItem(tree, item, mouseEvent, tree.getRowForPath(treePath))
        }
        else {
          button.onClick(mouseEvent.component, point.x, point.y, item)
        }
      }

      mouseEvent.consume()
    }

    override fun invokePopup(component: Component, x: Int, y: Int) {
      val sourceItem = getItem(TreeUtil.getPathForLocation(tree, x, y)) ?: return
      showPopup(RecentProjectRowButton.MoreActions.ACTION_GROUP_ID, component, x, y, sourceItem, getSelectedItems(tree))
    }

    // The button under [point], if any. Walks the row's buttons from the right, because the rightmost one is the only slot every row has.
    private fun rowButtonAt(point: Point?): RecentProjectRowButton? {
      if (point == null) return null
      val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
      if (row == -1) return null
      val buttons = rowButtonsAt(row)
      return buttons.lastOrNull { buttonRect(row, buttons, buttons.indexOf(it)).contains(point) }
    }

    // The buttons [row] shows, which the renderer and the geometry have to agree on.
    private fun rowButtonsAt(row: Int): List<RecentProjectRowButton> {
      val item = itemAt(row) ?: return emptyList()
      return rowButtons(item, isProjectValid(item), getRunningTasksOf(item), projectActionButtonViewModel.keptSpinnerIds)
    }

    private fun itemAt(row: Int): RecentProjectTreeItem? =
      (tree.getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? RecentProjectTreeItem

    private fun isProjectValid(item: RecentProjectTreeItem): Boolean =
      item !is RecentProjectItem || isProjectPathValid(item.projectPath)

    private fun getRunningTasksOf(item: RecentProjectTreeItem): List<RecentProjectProgress> =
      (item as? RecentProjectItem)?.let { projectsProgress.value.getProgressOf(it) }.orEmpty()

    /**
     * The bounds of the button at [index] of [buttons] on [row].
     *
     * The rightmost button is anchored to the row's edge, and each button to its left steps over its own gap to the neighbour on its
     * right - the gap belongs to the button that owns the space, which is the one being placed, not the one already placed.
     */
    private fun buttonRect(row: Int, buttons: List<RecentProjectRowButton>, index: Int): Rectangle {
      val size = JBUI.scale(ActionsButton.SIZE)
      var rect = rightmostButtonRect(row)
      for (i in buttons.lastIndex - 1 downTo index) {
        rect = Rectangle(rect.x - size - JBUIScale.scale(buttons[i].rightGap), rect.y, size, size)
      }
      return rect
    }

    // The rightmost button of [row]. Its gap to the edge belongs to the row, not to the button, because a group row indents differently.
    private fun rightmostButtonRect(row: Int): Rectangle {
      val helper = RenderingHelper(tree) // because the renderer's bounds are not full width
      val bounds = tree.getRowBounds(row)
      val size = JBUI.scale(ActionsButton.SIZE)

      val rightGap = when (itemAt(row)) {
        is ProjectsGroupItem -> JBUIScale.scale(ActionsButton.GROUP_RIGHT_GAP)
        else -> JBUIScale.scale(ActionsButton.RIGHT_GAP) + JBUIScale.scale(RENDERER_BORDER_SIZE)
      }

      return Rectangle(helper.width - helper.rightMargin - size - rightGap,
                       bounds.y + (bounds.height - size) / 2, size, size)
    }

    private fun RecentProjectRowButton.onClick(component: Component, x: Int, y: Int, item: RecentProjectTreeItem) {
      when (this) {
        is RecentProjectRowButton.VcsActions -> invokeVcsActions(item, buttonBottomLeft(this, x, y))
        is RecentProjectRowButton.TaskProgress -> toggleProgressPopup(TreeUtil.getRowForLocation(tree, x, y))
        is RecentProjectRowButton.MoreActions -> showPopup(ACTION_GROUP_ID, component, x, y, item)
        is RecentProjectRowButton.Remove -> item.removeItem()
      }
    }

    private fun invokeVcsActions(sourceItem: RecentProjectTreeItem, contextMenuPoint: Point) {
      val action = ActionManager.getInstance().getAction(RecentProjectRowButton.VcsActions.ACTION_ID) ?: return
      val dataContext = SimpleDataContext.builder()
        .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEMS_KEY, getSelectedItems(tree))
        .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY, sourceItem)
        .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_TREE_KEY, tree)
        .add(PlatformDataKeys.CONTEXT_MENU_POINT, contextMenuPoint)
        .build()
      ActionUtil.invokeAction(action, dataContext, ActionPlaces.WELCOME_SCREEN, null, null)
    }

    private fun buttonBottomLeft(button: RecentProjectRowButton, x: Int, y: Int): Point {
      val clicked = Point(x, y)
      val row = TreeUtil.getRowForLocation(tree, x, y)
      if (row == -1) return clicked
      val buttons = rowButtonsAt(row)
      val index = buttons.indexOf(button)
      if (index < 0) return clicked
      val rect = buttonRect(row, buttons, index)
      return Point(rect.x, rect.y + rect.height)
    }

    private fun showPopup(
      actionGroupId: String,
      component: Component,
      x: Int,
      y: Int,
      sourceItem: RecentProjectTreeItem,
      selectedItems: List<RecentProjectTreeItem> = emptyList(),
    ) {
      val popupMenu = buttonPopups.getOrPut(actionGroupId) {
        ActionManager.getInstance().let { actionManager ->
          val group = actionManager.getAction(actionGroupId) as ActionGroup
          actionManager.createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).also {
            it.component.addPopupMenuListener(updateHoverOnPopupClose)
          }
        }
      }
      popupMenu.setDataContext {
        SimpleDataContext.builder()
          .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEMS_KEY, selectedItems)
          .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY, sourceItem)
          .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_TREE_KEY, tree)
          .add(RecentProjectsFusEventFields.ROW_KEY, TreeUtil.getRowForLocation(tree, x, y).takeIf { it >= 0 })
          .build()
      }
      popupMenu.component.show(component, x, y)
    }

    private val updateHoverOnPopupClose = object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}

      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
        updateHoverAndSelection(tree.mousePosition)
      }

      override fun popupMenuCanceled(e: PopupMenuEvent) {}
    }
  }

  private class ToggleStateListener : TreeWillExpandListener {
    override fun treeWillExpand(event: TreeExpansionEvent) {
      setState(event, true)
    }

    override fun treeWillCollapse(event: TreeExpansionEvent) {
      setState(event, false)
    }

    private fun setState(event: TreeExpansionEvent, isExpanded: Boolean) {
      val item = TreeUtil.getLastUserObject(RecentProjectTreeItem::class.java, event.path) ?: return
      if (item is ProjectsGroupItem) {
        item.group.isExpanded = isExpanded
      }
    }
  }

  private class ProjectActionRenderer(
    private val isProjectPathValid: (String) -> Boolean,
    private val buttonViewModel: ProjectActionButtonViewModel,
    private val projectsProgress: StateFlow<ProjectsProgress>,
  ) : TreeCellRenderer {
    private val updateScaleHelper = UpdateScaleHelper()
    private val recentProjectComponent = RecentProjectComponent()
    private val projectGroupComponent = ProjectGroupComponent()
    private val cloneableProjectComponent = CloneableProjectComponent()

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean,
    ): Component? {
      updateScaleHelper.saveScaleAndRunIfChanged {
        updateScaleHelper.updateUIForAll(recentProjectComponent)
        updateScaleHelper.updateUIForAll(projectGroupComponent)
        updateScaleHelper.updateUIForAll(cloneableProjectComponent)
      }

      return when (val item = (value as DefaultMutableTreeNode).userObject as RecentProjectTreeItem) {
        is RecentProjectItem -> recentProjectComponent.customizeComponent(item, selected)
        is ProviderRecentProjectItem -> recentProjectComponent.customizeComponent(item, selected)
        is ProjectsGroupItem -> projectGroupComponent.customizeComponent(item, selected)
        is CloneableProjectItem -> cloneableProjectComponent.customizeComponent(item, selected)
        is RootItem -> null
      }
    }

    private abstract inner class RowComponent : JPanel(GridLayout()) {
      final override fun getToolTipText(event: MouseEvent): String? =
        buttonViewModel.hovered?.tooltip ?: super.getToolTipText(event)
    }

    private inner class RecentProjectComponent : RowComponent() {
      private val recentProjectsManager: RecentProjectsManagerBase
        get() = RecentProjectsManagerBase.getInstanceEx()

      private val projectNameLabel = JLabel()
      private val projectStatusLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val providerPathLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectPathLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectBranchNameLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
        icon = IconUtil.colorize(AllIcons.Vcs.Branch, UIUtil.getInactiveTextColor(), keepGray = false, keepBrightness = false)
      }
      private val projectIconLabel = JLabel()

      private val buttonSlots = RowButtonSlots(count = 3)
      private val projectNamePanel = JPanel(VerticalLayout(4)).apply {
        isOpaque = false

        val projectNameRow = JPanel(HorizontalLayout(4)).apply {
          isOpaque = false
          add(projectNameLabel)
          add(projectStatusLabel)
        }
        add(projectNameRow)
        add(providerPathLabel)
        add(projectPathLabel)
        add(projectBranchNameLabel)
      }
      private val projectProgressLabel = JLabel().apply {
        isOpaque = false
      }
      private val updateScaleHelper = UpdateScaleHelper()

      init {
        border = JBUI.Borders.empty(RENDERER_BORDER_SIZE)
        val builder = RowsGridBuilder(this)
          .cell(projectIconLabel,
                gaps = if (ExperimentalUI.isNewUI()) UnscaledGaps(6, 6, 0, 8) else UnscaledGaps(top = 8, right = 8),
                verticalAlign = VerticalAlign.TOP)
          .cell(projectNamePanel, resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, gaps = UnscaledGaps(4, 4, 4, 4))
          .cell(projectProgressLabel, resizableColumn = true, horizontalAlign = HorizontalAlign.RIGHT, gaps = UnscaledGaps(left = 8, right = 8))
        for ((index, slot) in buttonSlots.components.withIndex()) {
          val isLast = index == buttonSlots.components.lastIndex
          builder.cell(slot,
                       gaps = UnscaledGaps(right = if (isLast) ActionsButton.RIGHT_GAP else RecentProjectRowButton.VcsActions.rightGap))
        }
      }

      fun customizeComponent(item: RecentProjectItem, rowHovered: Boolean): JComponent {
        val isProjectValid = isProjectPathValid(item.projectPath)
        val projectPath = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
        val projectIcon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid, unscaledProjectIconSize())
        val tooltip = when {
          isProjectValid -> PathUtil.toSystemDependentName(projectPath)
          else -> PathUtil.toSystemDependentName(projectPath) + " " + IdeBundle.message("recent.project.unavailable")
        }
        customizeComponent(displayName = item.displayName,
                           projectPath = projectPath,
                           branchName = item.branchName,
                           providerPath = null,
                           tooltip = tooltip,
                           projectIcon = projectIcon,
                           isProjectValid = isProjectValid,
                           providerIcon = null)

        val operations = projectsProgress.value.getProgressOf(item)
        val buttons = rowButtons(item, isProjectValid, operations, buttonViewModel.keptSpinnerIds)
        // The spinner or its check shows without a hover, so it stays in sight while the cursor is in its window.
        val visibility = if (buttons.any { it is RecentProjectRowButton.TaskProgress }) SlotVisibility.ALWAYS else SlotVisibility.ON_HOVER
        buttonSlots.show(buttons, buttonViewModel.hovered, rowHovered, visibility)
        operations.forEach { task ->
          // The spinner replaces the progress bar the row used to show, and a spinner reads as nothing. Say what it stands for.
          AccessibleContextUtil.setName(this, IdeBundle.message("welcome.screen.recent.project.running.task.accessible.name",
                                                                accessibleContext.accessibleName,
                                                                task.text ?: task.title))
        }

        return this
      }

      fun customizeComponent(item: ProviderRecentProjectItem, rowHovered: Boolean): JComponent {
        val isProjectValid = true
        val projectIcon = item.icon
                          ?: recentProjectsManager.getNonLocalProjectIcon(item.projectId, isProjectValid,
                                                                          unscaledProjectIconSize(), item.displayName())
        customizeComponent(displayName = item.displayName(),
                           projectPath = item.projectPath,
                           branchName = item.branchName,
                           providerPath = item.providerPath,
                           tooltip = item.projectPath,
                           projectIcon = projectIcon,
                           isProjectValid = isProjectValid,
                           providerIcon = item.providerIcon)

        buttonSlots.show(rowButtons(item, isProjectValid), buttonViewModel.hovered, rowHovered, SlotVisibility.RESERVE_SPACE)

        if (item.statusText != null) {
          projectStatusLabel.isVisible = true
          projectStatusLabel.text = item.statusText
        }
        if (item.progressText != null) {
          projectProgressLabel.isVisible = true
          projectProgressLabel.icon = AnimatedIcon.Default.INSTANCE
          projectProgressLabel.text = item.progressText
        }

        return this
      }

      private fun customizeComponent(
        displayName: @NlsSafe String,
        projectPath: @NlsSafe String?,
        branchName: @NlsSafe String?,
        providerPath: @NlsSafe String?,
        tooltip: @NlsSafe String?,
        projectIcon: Icon,
        isProjectValid: Boolean,
        providerIcon: Icon?,
      ) {
        updateScaleHelper.saveScaleAndUpdateUIIfChanged(this)
        projectNameLabel.apply {
          text = displayName
          foreground = if (isProjectValid) UIUtil.getListForeground() else NamedColorUtil.getInactiveTextColor()
          accessibleContext.accessibleName =
            if (isProjectValid) displayName
            else IdeBundle.message("welcome.screen.recent.projects.name.label.unavailable.accessible.name", displayName)
        }
        providerPathLabel.apply {
          text = providerPath ?: ""
          isVisible = providerPath != null
          icon = providerIcon ?: AllIcons.Welcome.RecentProjects.RemoteProject
          verticalTextPosition = SwingConstants.CENTER
        }
        projectPathLabel.apply {
          text = projectPath ?: ""
          isVisible = projectPath != null
        }
        projectIconLabel.apply {
          icon = projectIcon
          disabledIcon = projectIcon
          isEnabled = isProjectValid
        }
        projectBranchNameLabel.apply {
          isVisible = branchName != null
          text = branchName ?: ""
          accessibleContext.accessibleName = IdeBundle.message("welcome.screen.recent.projects.branch.label.accessible.name", text)
        }

        projectStatusLabel.isVisible = false
        projectProgressLabel.isVisible = false
        buttonSlots.hide()

        if (tooltip != toolTipText) {
          serviceIfCreated<IdeTooltipManager>()?.hideCurrent(mouseEvent = null)
          toolTipText = tooltip
        }

        getAccessibleContext().accessibleName = AccessibleContextUtil.getCombinedName(
          ", ",
          projectNameLabel,
          projectStatusLabel.takeIf { projectStatusLabel.isVisible },
          projectProgressLabel.takeIf { projectProgressLabel.isVisible },
          providerPathLabel.takeIf { providerPathLabel.isVisible },
          projectPathLabel.takeIf { projectPathLabel.isVisible },
          projectBranchNameLabel.takeIf { projectBranchNameLabel.isVisible },
        )
        // Need to override the default description, which is the tooltip text,
        // because we already have the tooltip content in the accessible name.
        getAccessibleContext().accessibleDescription = ""
      }

      // Allow the recent project tree to reduce size of wide elements
      override fun getPreferredSize(): Dimension {
        val minSize = super.getPreferredSize()
        return Dimension(0, minSize.height)
      }
    }

    private inner class ProjectGroupComponent : RowComponent() {
      private val projectGroupNameLabel = SimpleColoredComponent().apply {
        isOpaque = false
      }
      private val buttonSlots = RowButtonSlots(count = 1)
      private val projectGroupActions = buttonSlots.components.single().apply {
        setState(AllIcons.Ide.Notification.Gear, false)
      }

      init {
        isOpaque = false

        RowsGridBuilder(this)
          .cell(projectGroupNameLabel, resizableColumn = true, gaps = UnscaledGaps(4, 4, 4, 4))
          .cell(projectGroupActions, gaps = UnscaledGaps(right = ActionsButton.GROUP_RIGHT_GAP))
      }

      fun customizeComponent(item: ProjectsGroupItem, rowHovered: Boolean): JComponent {
        projectGroupNameLabel.apply {
          clear()
          append(item.displayName(), SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getListForeground())) // NON-NLS
        }

        buttonSlots.show(rowButtons(item, isProjectValid = true), buttonViewModel.hovered, rowHovered)

        AccessibleContextUtil.setName(this, projectGroupNameLabel) // NON-NLS
        AccessibleContextUtil.setDescription(this, projectGroupNameLabel) // NON-NLS

        return this
      }
    }

    private inner class CloneableProjectComponent : RowComponent() {
      private val recentProjectsManager: RecentProjectsManagerBase
        get() = RecentProjectsManagerBase.getInstanceEx()

      private val projectNameLabel = JLabel().apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectPathLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectNamePanel = JPanel(VerticalLayout(4)).apply {
        isOpaque = false

        add(projectNameLabel)
        add(projectPathLabel)
      }
      private val projectIconLabel = JLabel().apply {
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
      }
      private var cancelButton: Boolean? = null
      private val buttonSlots = RowButtonSlots(count = 1)
      private val projectActionButton = buttonSlots.components.single()

      init {
        isOpaque = false
        border = JBUI.Borders.empty(RENDERER_BORDER_SIZE)

        RowsGridBuilder(this)
          .cell(projectIconLabel,
                gaps = if (ExperimentalUI.isNewUI()) UnscaledGaps(6, 6, 0, 8) else UnscaledGaps(top = 8, right = 8),
                verticalAlign = VerticalAlign.TOP)
          .cell(projectNamePanel, resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, gaps = UnscaledGaps(4, 4, 4, 4))
          .cell(projectActionButton, gaps = UnscaledGaps(right = ActionsButton.RIGHT_GAP))
      }

      fun customizeComponent(item: CloneableProjectItem, rowHovered: Boolean): JComponent {
        val cloneableProject = item.cloneableProject
        val taskInfo = cloneableProject.cloneTaskInfo
        val cloneStatus = cloneableProject.cloneStatus

        projectNameLabel.text = item.displayName() // NON-NLS
        projectPathLabel.text = FileUtil.getLocationRelativeToUserHome(item.projectPath.pathString, false)
        val buttons = rowButtons(item, isProjectValid = true, keptSpinnerIds = buttonViewModel.keptSpinnerIds)
        when (cancelButton) {
          // A clone in progress keeps its cancel button reachable without hovering the row.
          true -> buttonSlots.show(buttons, buttonViewModel.hovered, rowHovered, SlotVisibility.ALWAYS)
          false -> {
            // The check that stays after the clone ends shows without a hover, so it stays in sight while the cursor is in its window.
            val visibility = if (buttons.any { it is RecentProjectRowButton.TaskProgress }) SlotVisibility.ALWAYS else SlotVisibility.ON_HOVER
            buttonSlots.show(buttons, buttonViewModel.hovered, rowHovered, visibility)
          }
          else -> {}
        }
        toolTipText = null
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        when (cloneStatus) {
          CloneStatus.PROGRESS -> {
            projectIconLabel.icon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid = true)
            toolTipText = taskInfo.actionTooltipText
            cancelButton = true
          }
          CloneStatus.FAILURE -> {
            projectPathLabel.text = taskInfo.failedTitle
            projectIconLabel.icon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid = false)
            cancelButton = false
          }
          CloneStatus.CANCEL -> {
            projectPathLabel.text = taskInfo.canceledTitle
            projectIconLabel.icon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid = false)
            cancelButton = false
          }
          else -> {}
        }

        getAccessibleContext().accessibleName = AccessibleContextUtil.getCombinedName(
          ", ",
          projectNameLabel,
          projectPathLabel.takeIf { projectPathLabel.isVisible },
        )
        if (cloneStatus == CloneStatus.PROGRESS) {
          // The spinner replaces the progress bar the row used to show, and a spinner reads as nothing. Say what it stands for.
          AccessibleContextUtil.setName(this, IdeBundle.message("welcome.screen.recent.project.running.task.accessible.name",
                                                                accessibleContext.accessibleName, taskInfo.actionTitle))
        }

        return this
      }
    }
  }

  private class ProjectActionButtonViewModel(
    var hovered: RecentProjectRowButton? = null,
    /** The row whose spinner the cursor is on. */
    var hoveredSpinnerId: RowId? = null,
    /** The row whose process window is open. */
    var windowRowId: RowId? = null,
  ) {
    /**
     * The rows that keep their spinner after their operations end: the row whose spinner the cursor is on, and the row whose window is
     * open. A late click then reaches the spinner, and not the button or the row behind it.
     */
    val keptSpinnerIds: Set<RowId>
      get() = setOfNotNull(hoveredSpinnerId, windowRowId)
  }

  private class FullRendererComponentTreeUI : DefaultTreeUI() {
    override fun getPathBounds(tree: JTree, path: TreePath?): Rectangle? {
      val bounds = super.getPathBounds(tree, path)
      if (bounds != null) {
        bounds.width = bounds.width.coerceAtLeast(tree.width - bounds.x)
      }

      return bounds
    }

    override fun paintRow(
      g: Graphics, clipBounds: Rectangle,
      insets: Insets, bounds: Rectangle,
      path: TreePath, row: Int,
      isExpanded: Boolean, hasBeenExpanded: Boolean, isLeaf: Boolean,
    ) {
      if (tree != null) {
        bounds.width = tree.width
        val viewport = ComponentUtil.getViewport(tree)
        if (viewport != null) {
          bounds.width = viewport.width - viewport.viewPosition.x - insets.right / 2
        }
        bounds.width -= bounds.x
      }

      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf)
    }

    override fun getRowX(row: Int, depth: Int): Int {
      return JBUIScale.scale(getLeftMargin(depth - 1))
    }

    private fun getLeftMargin(level: Int): Int {
      return 3 + level * (11 + 5)
    }
  }

  private class MouseHoverListener(private val tree: Tree) : MouseMotionAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      val point = e.point
      val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
      if (row != -1) {
        UIUtil.setCursor(tree, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      }
      else {
        UIUtil.setCursor(tree, Cursor.getDefaultCursor())
      }
    }
  }

  companion object {

    private const val RENDERER_BORDER_SIZE = 4

    private fun createActionEvent(tree: Tree, inputEvent: InputEvent?, row: Int): AnActionEvent {
      val treeContext = DataManager.getInstance().getDataContext(tree)
      val dataContext = if (row < 0) treeContext
      else SimpleDataContext.getSimpleContext(RecentProjectsFusEventFields.ROW_KEY, row, treeContext)
      val actionPlace = UIUtil.uiParents(tree, true).let { parents ->
        for (parent in parents) {
          if (parent is FlatWelcomeFrame) return@let ActionPlaces.WELCOME_SCREEN
        }
        return@let ActionPlaces.POPUP
      }

      return if (inputEvent == null) AnActionEvent.createFromDataContext(actionPlace, null, dataContext)
      else AnActionEvent.createFromInputEvent(inputEvent, actionPlace, null, dataContext)
    }

    private fun activateItems(tree: Tree) {
      tree.selectionModel.selectionPaths.mapNotNull { path ->
        path.lastPathComponent.asSafely<DefaultMutableTreeNode>()?.let { path to it }
      }.forEach { (path, node) ->
        val item = node.userObject.asSafely<RecentProjectTreeItem>() ?: return
        activateItem(tree, item, row = tree.getRowForPath(path))
      }
    }

    private fun activateItem(tree: Tree, item: RecentProjectTreeItem, inputEvent: InputEvent? = null, row: Int = -1) {
      when (item) {
        is RecentProjectItem -> {
          val actionEvent = createActionEvent(tree, inputEvent, row)
          item.openProject(actionEvent)
        }
        is ProviderRecentProjectItem -> {
          val actionEvent = createActionEvent(tree, inputEvent, row)
          item.openProject(actionEvent)
        }
        is ProjectsGroupItem -> {
          val treePath = tree.selectionPath ?: return
          if (tree.isExpanded(treePath))
            tree.collapsePath(treePath)
          else
            tree.expandPath(treePath)
        }
        else -> {}
      }
    }

    private fun removeItem(tree: Tree) {
      val node = tree.lastSelectedPathComponent.asSafely<DefaultMutableTreeNode>() ?: return
      val item = node.userObject as RecentProjectTreeItem
      item.removeItem()
    }

    private fun getSelectedItem(tree: Tree): RecentProjectTreeItem? {
      return getItem(tree.selectionPath)
    }

    private fun getItem(path: TreePath?): RecentProjectTreeItem? {
      return TreeUtil.getLastUserObject(RecentProjectTreeItem::class.java, path)
    }

    private fun getSelectedItems(tree: Tree): List<RecentProjectTreeItem> {
      return tree.selectionPaths?.mapNotNull {
        getItem(it)
      } ?: emptyList()
    }
  }
}

private sealed class RecentProjectRowButton(
  val icon: Icon,
  val hoveredIcon: Icon = icon,
  /** Unscaled gap between this button and the button on its right. The rightmost button takes its gap from the row instead. */
  val rightGap: Int = ActionsButton.RIGHT_GAP,
) {
  abstract val tooltip: @NlsContexts.Tooltip String?

  /** Opens the version control actions of a recent project that is on a branch. */
  object VcsActions : RecentProjectRowButton(icon = AllIcons.Vcs.Branch, rightGap = ActionsButton.VCS_RIGHT_GAP) {
    const val ACTION_ID: String = "WelcomeScreen.Branches"

    override val tooltip: @NlsContexts.Tooltip String
      get() = IdeBundle.message("welcome.screen.recent.project.vcs.actions.tooltip")
  }

  /** Opens the row's action popup. */
  object MoreActions : RecentProjectRowButton(AllIcons.Ide.Notification.Gear, AllIcons.Ide.Notification.GearHover) {
    const val ACTION_GROUP_ID: String = "WelcomeScreenRecentProjectActionGroup"

    override val tooltip: @NlsContexts.Tooltip String
      get() = IdeBundle.message("welcome.screen.more.actions.link.text")
  }

  /** Drops the row from the list: a recent project that is no longer on disk, or a clone that failed or was canceled. */
  object Remove : RecentProjectRowButton(AllIcons.Welcome.RecentProjects.Remove, AllIcons.Welcome.RecentProjects.RemoveHover) {
    override val tooltip: @NlsContexts.Tooltip String
      get() = ActionsBundle.message("action.WelcomeScreen.RemoveSelected.text")
  }

  /**
   * Shows that the welcome screen runs an operation on a recent project.
   *
   * The row is too narrow for a progress bar, so it shows this spinner. A click on it opens [RecentProjectTaskProgressPopup], which
   * holds the bar and the cancel button of every operation the row runs. The spinner stays after the operations end while the cursor is
   * on it or while its window is open, see [rowButtons]. It then shows a check.
   */
  sealed class TaskProgress(icon: Icon) : RecentProjectRowButton(icon, rightGap = ActionsButton.VCS_RIGHT_GAP) {
    /** Null, because the window the click opens says what the operation is. */
    override val tooltip: @NlsContexts.Tooltip String? = null

    /** At least one operation of the row runs. */
    object Running : TaskProgress(AnimatedIcon.Default.INSTANCE)

    /** Every operation of the row has ended. */
    object Done : TaskProgress(AllIcons.Status.Success)
  }
}

/**
 * The buttons the row of [item] shows, from left to right.
 *
 * The row shows the spinner while [runningTasks] has an operation. It shows the check in that place while [keptSpinnerIds] holds the
 * id of the row and no operation runs.
 */
private fun rowButtons(
  item: RecentProjectTreeItem,
  isProjectValid: Boolean,
  runningTasks: List<RecentProjectProgress> = emptyList(),
  keptSpinnerIds: Set<RowId> = emptySet(),
): List<RecentProjectRowButton> {
  val isSpinnerKept = getRowId(item)?.let { it in keptSpinnerIds } == true
  return when (item) {
    is RecentProjectItem -> listOfNotNull(
      when {
        runningTasks.isNotEmpty() -> RecentProjectRowButton.TaskProgress.Running
        isSpinnerKept -> RecentProjectRowButton.TaskProgress.Done
        else -> null
      },
      RecentProjectRowButton.VcsActions.takeIf { isProjectValid && item.vcsActionsEnabled },
      if (isProjectValid) RecentProjectRowButton.MoreActions else RecentProjectRowButton.Remove,
    )
    is CloneableProjectItem -> when (item.cloneableProject.cloneStatus) {
      CloneStatus.PROGRESS -> listOf(RecentProjectRowButton.TaskProgress.Running)
      // The check takes the place of the one button a clone row has.
      CloneStatus.SUCCESS -> listOf(if (isSpinnerKept) RecentProjectRowButton.TaskProgress.Done else RecentProjectRowButton.MoreActions)
      CloneStatus.FAILURE, CloneStatus.CANCEL ->
        listOf(if (isSpinnerKept) RecentProjectRowButton.TaskProgress.Done else RecentProjectRowButton.Remove)
    }
    else -> listOf(RecentProjectRowButton.MoreActions)
  }
}

/** Tells the spinner and the window of one row from those of another. A path, because the tree rebuilds its items. */
@JvmInline
private value class RowId(val path: String)

/**
 * The id of the row of [item], or null for a row that runs no operation.
 *
 * A clone writes its path the way the recent project it becomes does, so a kept spinner stays on the row when the clone succeeds.
 */
private fun getRowId(item: RecentProjectTreeItem): RowId? = when (item) {
  is RecentProjectItem -> RowId(item.projectPath)
  is CloneableProjectItem -> RowId(item.projectPath.invariantSeparatorsPathString)
  else -> null
}

/** When a row shows its buttons. */
private enum class SlotVisibility {
  /** Only while the cursor is on the row, which is how a row keeps its buttons out of the way until wanted. */
  ON_HOVER,

  /** Always, hover or not: a clone in progress keeps its cancel button reachable. */
  ALWAYS,

  /** Always takes the space, but only draws the button on hover, so the row's text does not reflow as the cursor passes. */
  RESERVE_SPACE,
}

/**
 * Every operation the welcome screen runs on a recent project, in the window the status bar shows its own processes in.
 *
 * The row is too narrow for a progress bar, so the row shows one spinner and a click on it opens this. The window renders what
 * [operations] reports, and it stays open until the user closes it. An operation that ends takes its row away, so the window can be empty.
 */
private class RecentProjectTaskProgressPopup(
  private val rowId: RowId,
  private val windowTitle: @NlsContexts.PopupTitle String,
  private val operations: Flow<List<RecentProjectProgress>>,
  private val onCancel: (ProgressId) -> Unit,
  private val onClose: () -> Unit,
) {
  private val scope = service<CoreUiCoroutineScopeHolder>().coroutineScope.childScope("RecentProjectProcessWindow")
  private var window: Window? = null

  // One emission per report of any row. A row reports from the thread that runs its operation, which is a background one.
  private val repaintRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private class Window(val popup: ProcessPopup, val rows: ProgressRows)

  /** Whether this window is open on the operations of the row [id]. */
  fun isShowingRow(id: RowId): Boolean = window?.popup?.isShowing == true && rowId == id

  /**
   * Opens the window on the first operations [operations] reports, and renders what it reports from there.
   *
   * The first report arrives before the window opens, so it opens at the size its rows need. A later report adds and removes rows.
   */
  fun show(anchor: RelativePoint) {
    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      operations.collect { current ->
        when (val shown = window) {
          null -> open(anchor, current)
          else -> shown.rows.show(current)
        }
      }
    }
    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      repaintRequests.throttle(REPAINT_INTERVAL).collect { window?.rows?.repaint() }
    }
  }

  /** Closes the window and forgets it first, because hiding it calls back here. */
  fun close() {
    scope.cancel()
    val shown = window ?: return
    window = null
    shown.popup.hide()
    Disposer.dispose(shown.rows)
    onClose()
  }

  private fun open(anchor: RelativePoint, current: List<RecentProjectProgress>) {
    val popup = ProcessPopup(
      { anchor.component as JComponent },
      { close() },
      DIMENSION_SERVICE_KEY,
      windowTitle,
    )
    val rows = ProgressRows(popup, onRepaintNeeded = { repaintRequests.tryEmit(Unit) }, onCancel = onCancel)
    window = Window(popup, rows)
    rows.show(current)
    popup.show(true, anchor.screenPoint)
  }

  private companion object {
    /** Caps the repaints, however fast the operations report. The status bar caps its own rows at the same rate. */
    const val REPAINT_INTERVAL = 50L

    /** Its own key, so resizing this window leaves the size of the status bar's window alone. */
    const val DIMENSION_SERVICE_KEY = "RecentProjectProcessPopupWindow"
  }
}

private class RowButtonSlots(count: Int) {
  val components: List<ActionsButton> = List(count) { ActionsButton() }

  /** Shows [buttons] in the slots, and hides the slots this row does not use. */
  fun show(
    buttons: List<RecentProjectRowButton>,
    hovered: RecentProjectRowButton?,
    rowHovered: Boolean,
    visibility: SlotVisibility = SlotVisibility.ON_HOVER,
  ) {
    val firstUsedSlot = components.size - buttons.size
    for ((index, slot) in components.withIndex()) {
      val button = buttons.getOrNull(index - firstUsedSlot)
      if (button == null) {
        slot.isVisible = false
        continue
      }
      // The spinner turns into the check under a cursor that does not move, so the hover goes with it.
      val isHoveredButton =
        hovered == button || hovered is RecentProjectRowButton.TaskProgress && button is RecentProjectRowButton.TaskProgress
      val buttonHovered = isHoveredButton && rowHovered
      val icon = if (buttonHovered) button.hoveredIcon else button.icon
      when (visibility) {
        SlotVisibility.ON_HOVER -> {
          slot.isVisible = rowHovered
          slot.setState(icon, buttonHovered)
        }
        SlotVisibility.ALWAYS -> {
          slot.isVisible = true
          slot.setState(icon, buttonHovered)
        }
        SlotVisibility.RESERVE_SPACE -> {
          slot.isVisible = true
          slot.setState(if (rowHovered) icon else EmptyIcon.create(icon), buttonHovered)
        }
      }
    }
  }

  fun hide() {
    components.forEach { it.isVisible = false }
  }
}

private class ActionsButton : SelectablePanel() {
  companion object {
    const val SIZE = 22
    const val RIGHT_GAP = 20
    const val GROUP_RIGHT_GAP = 14
    const val VCS_RIGHT_GAP = 4
  }

  private val label = JLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.CENTER
  }

  init {
    isOpaque = false
    preferredSize = JBDimension(SIZE, SIZE)
    layout = BorderLayout()
    add(label, BorderLayout.CENTER)
    selectionArc = JBUI.scale(6)
  }

  fun setState(icon: Icon, hovered: Boolean) {
    label.icon = IconUtil.toSize(icon, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
    selectionColor = if (hovered) JBUI.CurrentTheme.List.buttonHoverBackground() else null
  }
}

private val MouseEvent.isMultipleSelectionInProgress: Boolean
  get() = UIUtil.isControlKeyDown(this) || isShiftDown

internal class ProviderProjectAdditionalActionsGroup : ActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val item = e?.getData(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY) as? ProviderRecentProjectItem
               ?: return EMPTY_ARRAY
    return item.additionalActions.toTypedArray()
  }
}
