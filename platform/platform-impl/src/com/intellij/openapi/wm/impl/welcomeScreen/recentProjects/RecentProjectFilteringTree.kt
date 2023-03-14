// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase
import com.intellij.ui.*
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.hover.TreeHoverListener
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
import com.intellij.util.ui.*
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

internal class RecentProjectFilteringTree(
  treeComponent: Tree,
  parentDisposable: Disposable,
  collectors: List<() -> List<RecentProjectTreeItem>>
) : FilteringTree<DefaultMutableTreeNode, RecentProjectTreeItem>(treeComponent, DefaultMutableTreeNode(RootItem(collectors))) {
  init {
    val projectActionButtonViewModel = ProjectActionButtonViewModel()
    val filePathChecker = createFilePathChecker()
    Disposer.register(parentDisposable, filePathChecker)

    // Provide data context for projects actions
    DataManager.registerDataProvider(treeComponent) { dataId ->
      when {
        RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY.`is`(dataId) -> getSelectedItem(tree)
        RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_TREE_KEY.`is`(dataId) -> tree
        else -> null
      }
    }

    treeComponent.addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItem(treeComponent) }

    val group = ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup") as ActionGroup
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group)
    val mouseListener = ProjectActionMouseListener(treeComponent, projectActionButtonViewModel, filePathChecker::isValid, popupMenu)
    treeComponent.addMouseListener(mouseListener)
    treeComponent.addMouseMotionListener(mouseListener)
    treeComponent.addTreeWillExpandListener(ToggleStateListener())

    treeComponent.putClientProperty(Control.Painter.KEY, Control.Painter.LEAF_WITHOUT_INDENT)
    treeComponent.putClientProperty(
      RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
      Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), isSelected = true, hasFocus = true) }
    )

    SmartExpander.installOn(treeComponent)
    TreeHoverToSelectionListener(popupMenu).addTo(treeComponent)

    treeComponent.isRootVisible = false
    treeComponent.cellRenderer = ProjectActionRenderer(filePathChecker::isValid, projectActionButtonViewModel)
    treeComponent.rowHeight = 0 // Fix tree renderer size on macOS
    treeComponent.background = WelcomeScreenUIManager.getProjectsBackground()
    treeComponent.toggleClickCount = 0

    treeComponent.setUI(FullRendererComponentTreeUI())
    treeComponent.setExpandableItemsEnabled(false)

    treeComponent.addMouseMotionListener(MouseHoverListener(treeComponent))

    searchModel.updateStructure()
  }

  fun updateTree() {
    searchModel.updateStructure()
    expandGroups()
  }

  override fun getNodeClass() = DefaultMutableTreeNode::class.java

  override fun getText(item: RecentProjectTreeItem?): String = when (item) {
    is RecentProjectItem -> item.searchName()
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
        border = JBUI.Borders.empty()
        emptyText.text = IdeBundle.message("welcome.screen.search.projects.empty.text")
        accessibleContext.accessibleName = IdeBundle.message("welcome.screen.search.projects.empty.text")
        FragmentedSettingsUtil.setupPlaceholderVisibility(this)

        addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItem(tree) }
        addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.ALT_DOWN_MASK)) { removeItem(tree) }
      }
    }
  }

  override fun expandTreeOnSearchUpdateComplete(pattern: String?) {
    TreeUtil.expandAll(tree)
  }

  override fun useIdentityHashing(): Boolean = false

  private fun createFilePathChecker(): RecentProjectPanel.FilePathChecker {
    val recentProjectTreeItems: List<RecentProjectTreeItem> = RecentProjectListActionProvider.getInstance().collectProjects()
    val recentProjects = mutableListOf<RecentProjectItem>()
    for (item in recentProjectTreeItems) {
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

  internal fun selectLastOpenedProject() {
    val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
    val projectPath = recentProjectsManager.getLastOpenedProject() ?: return

    val node = TreeUtil.findNode(root, Condition {
      when (val item = TreeUtil.getUserObject(RecentProjectTreeItem::class.java, it)) {
        is RecentProjectItem -> item.projectPath == projectPath
        is CloneableProjectItem -> item.projectPath == projectPath
        else -> false
      }
    })

    if (node != null) {
      TreeUtil.selectNode(tree, node)
    }
  }

  private class TreeHoverToSelectionListener(private val popupMenu: ActionPopupMenu) : TreeHoverListener() {
    override fun onHover(tree: JTree, row: Int) {
      if (!popupMenu.component.isVisible) {
        tree.setSelectionRow(row)
      }
    }
  }

  private class ProjectActionMouseListener(
    private val tree: Tree,
    private val projectActionButtonViewModel: ProjectActionButtonViewModel,
    private val isProjectPathValid: (String) -> Boolean,
    private val popupMenu: ActionPopupMenu
  ) : PopupHandler() {

    override fun mouseMoved(mouseEvent: MouseEvent) {
      val point = mouseEvent.point
      val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
      if (row != -1) {
        // Repaint whole row to avoid flickering of row buttons
        tree.repaint(tree.getRowBounds(row))
      }

      projectActionButtonViewModel.isButtonHovered = intersectWithActionIcon(point)
    }

    override fun mouseReleased(mouseEvent: MouseEvent) {
      super.mouseReleased(mouseEvent)

      if (mouseEvent.isConsumed) {
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
        if (intersectWithActionIcon(point)) {
          when (item) {
            is CloneableProjectItem -> {
              when (item.cloneableProject.cloneStatus) {
                CloneStatus.SUCCESS -> invokePopup(mouseEvent.component, point.x, point.y, item)
                CloneStatus.PROGRESS -> cancelCloneProject(item.cloneableProject)
                CloneStatus.FAILURE -> item.removeItem()
                CloneStatus.CANCEL -> item.removeItem()
              }
            }
            is RecentProjectItem -> {
              if (isProjectPathValid(item.projectPath)) {
                invokePopup(mouseEvent.component, point.x, point.y, item)
              }
              else {
                item.removeItem()
              }
            }
            else -> invokePopup(mouseEvent.component, point.x, point.y, item)
          }
        }
        else {
          activateItem(tree, item, mouseEvent)
        }
      }

      mouseEvent.consume()
    }

    override fun invokePopup(component: Component, x: Int, y: Int) {
      val item = getSelectedItem(tree) ?: return
      invokePopup(component, x, y, item)
    }

    private fun invokePopup(component: Component, x: Int, y: Int, item: RecentProjectTreeItem) {
      popupMenu.setDataContext {
        SimpleDataContext.builder()
          .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_SELECTED_ITEM_KEY, item)
          .add(RecentProjectsWelcomeScreenActionBase.RECENT_PROJECT_TREE_KEY, tree)
          .build()
      }
      popupMenu.component.show(component, x, y)
    }

    private fun intersectWithActionIcon(point: Point): Boolean {
      val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
      return row != -1 && getActionsButtonRect(row).contains(point)
    }

    private fun getActionsButtonRect(row: Int): Rectangle {
      val helper = RenderingHelper(tree) // because the renderer's bounds are not full width
      val bounds = tree.getRowBounds(row)
      val size = JBUI.scale(ActionsButton.SIZE)

      val node = tree.getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode
      val rightGap = when (node?.userObject) {
        is ProjectsGroupItem -> JBUIScale.scale(ActionsButton.GROUP_RIGHT_GAP)
        else -> JBUIScale.scale(ActionsButton.RIGHT_GAP) + JBUIScale.scale(RENDERER_BORDER_SIZE)
      }

      return Rectangle(helper.width - helper.rightMargin - size - rightGap,
                       bounds.y + (bounds.height - size) / 2, size, size)
    }

    private fun cancelCloneProject(cloneableProject: CloneableProject) {
      val taskInfo = cloneableProject.cloneTaskInfo
      val exitCode = Messages.showYesNoDialog(
        taskInfo.stopDescription,
        taskInfo.stopTitle,
        IdeBundle.message("action.stop"),
        IdeBundle.message("button.cancel"),
        Messages.getQuestionIcon()
      )

      if (exitCode == Messages.OK) {
        CloneableProjectsService.getInstance().cancelClone(cloneableProject)
      }
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
    private val buttonViewModel: ProjectActionButtonViewModel
  ) : TreeCellRenderer {
    private val recentProjectComponent = RecentProjectComponent()
    private val projectGroupComponent = ProjectGroupComponent()
    private val cloneableProjectComponent = CloneableProjectComponent()

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component? {
      return when (val item = (value as DefaultMutableTreeNode).userObject as RecentProjectTreeItem) {
        is RecentProjectItem -> recentProjectComponent.customizeComponent(item, selected)
        is ProjectsGroupItem -> projectGroupComponent.customizeComponent(item, selected)
        is CloneableProjectItem -> cloneableProjectComponent.customizeComponent(item, selected)
        is RootItem -> null
      }
    }

    private inner class RecentProjectComponent : JPanel(GridLayout()) {
      private val recentProjectsManager: RecentProjectsManagerBase
        get() = RecentProjectsManagerBase.getInstanceEx()

      private val projectNameLabel = JLabel()
      private val projectPathLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("").apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectIconLabel = JLabel()
      private val projectActions = ActionsButton().apply {
        setState(AllIcons.Ide.Notification.Gear, false)
      }
      private val projectNamePanel = JPanel(VerticalLayout(4)).apply {
        isOpaque = false

        add(projectNameLabel)
        add(projectPathLabel)
      }

      init {
        border = JBUI.Borders.empty(RENDERER_BORDER_SIZE)
        RowsGridBuilder(this)
          .cell(projectIconLabel,
                gaps = if (ExperimentalUI.isNewUI()) JBGaps(6, 6, 0, 8) else JBGaps(top = 8, right = 8),
                verticalAlign = VerticalAlign.TOP)
          .cell(projectNamePanel, resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, gaps = JBGaps(4, 4, 4, 4))
          .cell(projectActions, gaps = JBGaps(right = ActionsButton.RIGHT_GAP))
      }

      fun customizeComponent(item: RecentProjectItem, rowHovered: Boolean): JComponent {
        val isPathValid = isProjectPathValid(item.projectPath)
        projectNameLabel.apply {
          text = item.displayName
          foreground = if (isPathValid) UIUtil.getListForeground() else NamedColorUtil.getInactiveTextColor()
        }
        projectPathLabel.apply {
          text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
        }
        projectIconLabel.apply {
          icon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid = true)
          disabledIcon = recentProjectsManager.getProjectIcon(item.projectPath, isProjectValid = false)
          isEnabled = isPathValid
        }
        if (isPathValid) {
          buttonViewModel.prepareActionsButton(projectActions, rowHovered, AllIcons.Ide.Notification.Gear,
                                               AllIcons.Ide.Notification.GearHover)
        }
        else {
          buttonViewModel.prepareActionsButton(projectActions, rowHovered, AllIcons.Welcome.Project.Remove,
                                               AllIcons.Welcome.Project.RemoveHover)
        }

        val toolTipPath = PathUtil.toSystemDependentName(item.projectPath)
        val tooltip = if (isPathValid) toolTipPath else "$toolTipPath ${IdeBundle.message("recent.project.unavailable")}"
        if (tooltip != toolTipText) {
          IdeTooltipManager.getInstance().hideCurrent(null)
          toolTipText = tooltip
        }

        AccessibleContextUtil.setCombinedName(this, projectNameLabel, "-", projectPathLabel) // NON-NLS
        AccessibleContextUtil.setCombinedDescription(this, projectNameLabel, "-", projectPathLabel) // NON-NLS

        return this
      }
    }

    private inner class ProjectGroupComponent : JPanel(GridLayout()) {

      private val projectGroupNameLabel = SimpleColoredComponent().apply {
        isOpaque = false
      }
      private val projectGroupActions = ActionsButton().apply {
        setState(AllIcons.Ide.Notification.Gear, false)
      }

      init {
        isOpaque = false

        RowsGridBuilder(this)
          .cell(projectGroupNameLabel, resizableColumn = true, gaps = JBGaps(4, 4, 4, 4))
          .cell(projectGroupActions, gaps = JBGaps(right = ActionsButton.GROUP_RIGHT_GAP))
      }

      fun customizeComponent(item: ProjectsGroupItem, rowHovered: Boolean): JComponent {
        projectGroupNameLabel.apply {
          clear()
          append(item.displayName(), SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getListForeground())) // NON-NLS
        }

        buttonViewModel.prepareActionsButton(projectGroupActions, rowHovered, AllIcons.Ide.Notification.Gear,
                                             AllIcons.Ide.Notification.GearHover)

        AccessibleContextUtil.setName(this, projectGroupNameLabel) // NON-NLS
        AccessibleContextUtil.setDescription(this, projectGroupNameLabel) // NON-NLS

        return this
      }
    }

    private inner class CloneableProjectComponent : JPanel(GridLayout()) {
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
      private val projectActionButton = ActionsButton()
      private val projectProgressLabel = JLabel().apply {
        foreground = NamedColorUtil.getInactiveTextColor()
      }
      private val projectProgressBar = JProgressBar().apply {
        isOpaque = false
      }
      private val projectProgressBarPanel = object : BorderLayoutPanel() {
        init {
          isOpaque = false
        }

        override fun getPreferredSize(): Dimension {
          val size = super.getPreferredSize()
          size.width = PROGRESS_BAR_WIDTH
          return size
        }
      }.apply {
        add(projectProgressLabel, BorderLayout.NORTH)
        add(projectProgressBar, BorderLayout.SOUTH)
      }

      init {
        isOpaque = false
        border = JBUI.Borders.empty(RENDERER_BORDER_SIZE)

        RowsGridBuilder(this)
          .cell(projectIconLabel,
                gaps = if (ExperimentalUI.isNewUI()) JBGaps(6, 6, 0, 8) else JBGaps(top = 8, right = 8),
                verticalAlign = VerticalAlign.TOP)
          .cell(projectNamePanel, resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, gaps = JBGaps(4, 4, 4, 4))
          .cell(projectProgressBarPanel, gaps = JBGaps(left = 8, right = 8))
          .cell(projectActionButton, gaps = JBGaps(right = ActionsButton.RIGHT_GAP))
      }

      fun customizeComponent(item: CloneableProjectItem, rowHovered: Boolean): JComponent {
        val cloneableProject = item.cloneableProject
        val taskInfo = cloneableProject.cloneTaskInfo
        val progressIndicator = cloneableProject.progressIndicator
        val cloneStatus = cloneableProject.cloneStatus

        projectNameLabel.text = item.displayName() // NON-NLS
        projectPathLabel.text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
        when (cancelButton) {
          true -> {
            buttonViewModel.prepareActionsButton(projectActionButton, rowHovered, AllIcons.Actions.DeleteTag,
                                                 AllIcons.Actions.DeleteTagHover)
            projectActionButton.isVisible = true // always visible
          }
          false -> {
            buttonViewModel.prepareActionsButton(projectActionButton, rowHovered, AllIcons.Welcome.Project.Remove,
                                                 AllIcons.Welcome.Project.RemoveHover)
          }
          else -> {}
        }
        projectProgressBarPanel.apply {
          isVisible = false
          isEnabled = false
        }
        toolTipText = null
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        projectProgressBar.apply {
          val fraction = progressIndicator.fraction
          if (fraction <= 0.0 || progressIndicator.isIndeterminate) {
            isIndeterminate = true
            val progressBarUI = projectProgressBar.ui
            if (progressBarUI is DarculaProgressBarUI) {
              progressBarUI.updateIndeterminateAnimationIndex(START_MILLIS)
            }
          }
          else {
            isIndeterminate = false
            value = (fraction * 100).toInt()
          }
        }

        when (cloneStatus) {
          CloneStatus.PROGRESS -> {
            projectProgressBarPanel.apply {
              isVisible = true
              isEnabled = true
            }
            projectProgressLabel.text = taskInfo.actionTitle
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

        return this
      }
    }

    companion object {
      private const val START_MILLIS = 0L
      private const val PROGRESS_BAR_WIDTH = 200
    }
  }

  private class ProjectActionButtonViewModel(
    var isButtonHovered: Boolean = false
  ) {

    fun prepareActionsButton(button: ActionsButton, rowHovered: Boolean, icon: Icon, hoveredIcon: Icon) {
      val hovered = isButtonHovered && rowHovered
      button.isVisible = rowHovered
      button.setState(if (hovered) hoveredIcon else icon, hovered)
    }
  }

  private class FullRendererComponentTreeUI : DefaultTreeUI() {
    override fun getPathBounds(tree: JTree, path: TreePath?): Rectangle? {
      val bounds = super.getPathBounds(tree, path)
      if (bounds != null) {
        bounds.width = bounds.width.coerceAtLeast(tree.width - bounds.x)
      }

      return bounds
    }

    override fun paintRow(g: Graphics, clipBounds: Rectangle,
                          insets: Insets, bounds: Rectangle,
                          path: TreePath, row: Int,
                          isExpanded: Boolean, hasBeenExpanded: Boolean, isLeaf: Boolean) {
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

    private fun createActionEvent(tree: Tree, inputEvent: InputEvent?): AnActionEvent {
      val dataContext = DataManager.getInstance().getDataContext(tree)
      val actionPlace =
        if (UIUtil.uiParents(tree, true).filter(FlatWelcomeFrame::class.java).isEmpty) ActionPlaces.POPUP
        else ActionPlaces.WELCOME_SCREEN

      return if (inputEvent == null) AnActionEvent.createFromDataContext(actionPlace, null, dataContext)
      else AnActionEvent.createFromInputEvent(inputEvent, actionPlace, null, dataContext)
    }

    private fun activateItem(tree: Tree) {
      val node = tree.lastSelectedPathComponent.asSafely<DefaultMutableTreeNode>() ?: return
      val item = node.userObject.asSafely<RecentProjectTreeItem>() ?: return
      activateItem(tree, item)
    }

    private fun activateItem(tree: Tree, item: RecentProjectTreeItem, inputEvent: InputEvent? = null) {
      when (item) {
        is RecentProjectItem -> {
          val actionEvent = createActionEvent(tree, inputEvent)
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
      return TreeUtil.getLastUserObject(RecentProjectTreeItem::class.java, tree.selectionPath)
    }
  }
}

private class ActionsButton : SelectablePanel() {

  companion object {
    const val SIZE = 22
    const val RIGHT_GAP = 20
    const val GROUP_RIGHT_GAP = 14
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
