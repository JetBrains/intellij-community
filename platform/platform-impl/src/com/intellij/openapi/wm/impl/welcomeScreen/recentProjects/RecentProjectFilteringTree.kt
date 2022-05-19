// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.ui.*
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.IconUtil
import com.intellij.util.PathUtil
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

class RecentProjectFilteringTree(
  treeComponent: Tree,
  parentDisposable: Disposable
) : FilteringTree<DefaultMutableTreeNode, RecentProjectTreeItem>(
  ProjectManager.getInstance().defaultProject,
  treeComponent,
  DefaultMutableTreeNode(RootItem)
) {
  init {
    treeComponent.apply {
      val filePathChecker = createFilePathChecker()
      Disposer.register(parentDisposable, filePathChecker)

      setEmptyState(IdeBundle.message("empty.text.no.project.open.yet"))
      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItem(this) }
      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)) { removeItem(this) }
      addMouseListener(ProjectActionMouseListener(this))
      addTreeWillExpandListener(ToggleStateListener())
      putClientProperty(Control.Painter.KEY, Control.Painter.LEAF_WITHOUT_INDENT)
      putClientProperty(
        RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
        Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), true, true) }
      )

      SmartExpander.installOn(this)
      TreeHoverToSelectionListener().addTo(this)

      isRootVisible = false
      cellRenderer = ProjectActionRenderer(filePathChecker::isValid)
      rowHeight = 0 // Fix tree renderer size on macOS
      background = WelcomeScreenUIManager.getProjectsBackground()
      toggleClickCount = 1

      setExpandableItemsEnabled(false)
    }
  }

  fun updateTree() {
    searchModel.updateStructure()
    expandGroups()
  }

  override fun getNodeClass() = DefaultMutableTreeNode::class.java

  override fun getText(item: RecentProjectTreeItem?): String = item?.displayName().orEmpty()

  override fun getChildren(item: RecentProjectTreeItem): Iterable<RecentProjectTreeItem> = item.children()

  override fun createNode(item: RecentProjectTreeItem): DefaultMutableTreeNode = DefaultMutableTreeNode(item)

  override fun rebuildTree() {
    expandGroups()
  }

  override fun createSpeedSearch(searchTextField: SearchTextField): SpeedSearchSupply = object : FilteringSpeedSearch(searchTextField) {}

  override fun installSearchField(): SearchTextField {
    return super.installSearchField().apply {
      isOpaque = false
      border = JBUI.Borders.empty()

      textEditor.apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        emptyText.text = IdeBundle.message("welcome.screen.search.projects.empty.text")
        accessibleContext.accessibleName = IdeBundle.message("welcome.screen.search.projects.empty.text")

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

    val treeUpdater = Runnable { searchModel.updateStructure() }

    return RecentProjectPanel.FilePathChecker(treeUpdater, recentProjects.map { it.projectPath })
  }

  private fun expandGroups() {
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

  private class TreeHoverToSelectionListener : TreeHoverListener() {
    override fun onHover(tree: JTree, row: Int) {
      if (row != -1) {
        tree.setSelectionRow(row)
      }
    }
  }

  private class ProjectActionMouseListener(private val tree: Tree) : PopupHandler() {
    override fun mousePressed(mouseEvent: MouseEvent) {
      super.mousePressed(mouseEvent)

      if (mouseEvent.isConsumed || EditSourceOnDoubleClickHandler.isToggleEvent(tree, mouseEvent)) {
        return
      }

      val point = mouseEvent.point
      val treePath = TreeUtil.getPathForLocation(tree, point.x, point.y) ?: return
      val item = TreeUtil.getLastUserObject(RecentProjectTreeItem::class.java, treePath) ?: return

      if (intersectWithActionIcon(point)) {
        when (item) {
          is CloneableProjectItem -> cancelCloneProject(item.cloneableProject)
          else -> invokePopup(mouseEvent.component, point.x, point.y)
        }
      }
      else activateItem(mouseEvent, item)

      mouseEvent.consume()
    }

    override fun invokePopup(component: Component, x: Int, y: Int) {
      val group = ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup") as ActionGroup
      ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).component.show(component, x, y)
    }

    private fun intersectWithActionIcon(point: Point): Boolean {
      val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
      return row != -1 && getCloseIconRect(row).contains(point)
    }

    private fun activateItem(mouseEvent: MouseEvent, item: RecentProjectTreeItem) {
      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        if (item is RecentProjectItem) {
          item.openProject(createActionEvent(tree))
        }
      }
    }

    private fun getCloseIconRect(row: Int): Rectangle {
      val helper = RenderingHelper(tree) // because the renderer's bounds are not full width
      val bounds = tree.getRowBounds(row)
      val icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                                 ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                                 ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())

      return Rectangle(helper.width - helper.rightMargin - icon.iconWidth - JBUIScale.scale(14),
                       bounds.y + (bounds.height - icon.iconHeight) / 2,
                       icon.iconWidth, icon.iconHeight)
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
    private val isProjectPathValid: (String) -> Boolean
  ) : TreeCellRenderer {
    private val recentProjectComponent = RecentProjectComponent()
    private val projectGroupComponent = ProjectGroupComponent()
    private val cloneableProjectComponent = CloneableProjectComponent()

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
      return when (val item = (value as DefaultMutableTreeNode).userObject as RecentProjectTreeItem) {
        is RootItem -> JBUI.Panels.simplePanel()
        is RecentProjectItem -> recentProjectComponent.customizeComponent(item, selected, isProjectPathValid(item.projectPath))
        is ProjectsGroupItem -> projectGroupComponent.customizeComponent(item, selected)
        is CloneableProjectItem -> cloneableProjectComponent.customizeComponent(item)
      }
    }

    private class RecentProjectComponent : BorderLayoutPanel() {
      private val recentProjectsManager: RecentProjectsManagerBase
        get() = RecentProjectsManagerBase.instanceEx

      private val projectNameLabel = JLabel()
      private val projectPathLabel = JLabel().apply {
        foreground = UIUtil.getInactiveTextColor()
      }
      private val projectIconLabel = JLabel().apply {
        border = JBUI.Borders.empty(8, 0, 0, 8)
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
      }
      private val projectActions = JLabel().apply {
        border = JBUI.Borders.empty(0, 0, 0, 10)
        icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())
      }
      private val projectNamePanel = JBUI.Panels.simplePanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty(4)

        add(projectNameLabel, BorderLayout.NORTH)
        add(projectPathLabel, BorderLayout.SOUTH)
      }

      init {
        border = JBUI.Borders.empty(4)

        add(projectNamePanel, BorderLayout.CENTER)
        add(projectIconLabel, BorderLayout.WEST)
        add(projectActions, BorderLayout.EAST)
      }

      fun customizeComponent(item: RecentProjectItem, isSelected: Boolean, isProjectPathValid: Boolean): JComponent {
        projectNameLabel.apply {
          text = item.displayName
          foreground = if (isProjectPathValid) UIUtil.getListForeground() else UIUtil.getInactiveTextColor()
        }
        projectPathLabel.apply {
          text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
        }
        projectIconLabel.apply {
          icon = recentProjectsManager.getProjectIcon(item.projectPath, true)
          disabledIcon = IconUtil.desaturate(icon)
          isEnabled = isProjectPathValid
        }
        projectActions.isVisible = isSelected

        val toolTipPath = PathUtil.toSystemDependentName(item.projectPath)
        toolTipText = if (isProjectPathValid) toolTipPath else "$toolTipPath ${IdeBundle.message("recent.project.unavailable")}"

        AccessibleContextUtil.setCombinedName(this, projectNameLabel, "-", projectPathLabel) // NON-NLS
        AccessibleContextUtil.setCombinedDescription(this, projectNameLabel, "-", projectPathLabel) // NON-NLS

        return this
      }
    }

    private class ProjectGroupComponent : BorderLayoutPanel() {
      private val projectGroupNameLabel = SimpleColoredComponent().apply {
        isOpaque = false
        border = JBUI.Borders.empty(4)
      }
      private val projectGroupActions = JLabel().apply {
        border = JBUI.Borders.empty(0, 0, 0, 14)
        icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())
      }

      init {
        isOpaque = false

        add(projectGroupNameLabel, BorderLayout.WEST)
        add(projectGroupActions, BorderLayout.EAST)
      }

      fun customizeComponent(item: ProjectsGroupItem, isSelected: Boolean): JComponent {
        projectGroupNameLabel.apply {
          clear()
          append(item.displayName(), SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getListForeground())) // NON-NLS
        }
        projectGroupActions.isVisible = isSelected

        AccessibleContextUtil.setName(this, projectGroupNameLabel) // NON-NLS
        AccessibleContextUtil.setDescription(this, projectGroupNameLabel) // NON-NLS

        return this
      }
    }

    private class CloneableProjectComponent : BorderLayoutPanel() {
      private val recentProjectsManager: RecentProjectsManagerBase
        get() = RecentProjectsManagerBase.instanceEx

      private val projectNameLabel = JLabel().apply {
        foreground = UIUtil.getInactiveTextColor()
      }
      private val projectPathLabel = JLabel().apply {
        foreground = UIUtil.getInactiveTextColor()
      }
      private val projectNamePanel = JBUI.Panels.simplePanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty(4)

        add(projectNameLabel, BorderLayout.NORTH)
        add(projectPathLabel, BorderLayout.SOUTH)
      }
      private val projectIconLabel = JLabel().apply {
        border = JBUI.Borders.empty(8, 0, 0, 8)
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
      }
      private val projectCancelButton = JLabel().apply {
        icon = AllIcons.Actions.DeleteTag
        border = JBUI.Borders.empty(0, 0, 0, 14)
      }
      private val projectProgressLabel = JLabel().apply {
        foreground = UIUtil.getInactiveTextColor()
      }
      private val projectProgressBar = JProgressBar().apply {
        isOpaque = false
      }
      private val projectProgressBarPanel = JBUI.Panels.simplePanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty(8)

        add(projectProgressLabel, BorderLayout.NORTH)
        add(projectProgressBar, BorderLayout.SOUTH)
      }
      private val projectCloneStatusPanel = JBUI.Panels.simplePanel().apply {
        isOpaque = false

        add(projectProgressBarPanel, BorderLayout.CENTER)
        add(projectCancelButton, BorderLayout.EAST)
      }

      init {
        isOpaque = false
        border = JBUI.Borders.empty(4)

        add(projectNamePanel, BorderLayout.CENTER)
        add(projectIconLabel, BorderLayout.WEST)
        add(projectCloneStatusPanel, BorderLayout.EAST)
      }

      fun customizeComponent(item: CloneableProjectItem): JComponent {
        val cloneableProject = item.cloneableProject
        val taskInfo = cloneableProject.cloneTaskInfo
        val progressIndicator = cloneableProject.progressIndicator
        val cloneStatus = cloneableProject.cloneStatus

        projectNameLabel.text = item.displayName() // NON-NLS
        projectPathLabel.text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)

        projectProgressBar.apply {
          val isProgressIndeterminate = progressIndicator.isIndeterminate
          if (isProgressIndeterminate) {
            val progressBarUI = projectProgressBar.ui
            if (progressBarUI is DarculaProgressBarUI) {
              progressBarUI.updateIndeterminateAnimationIndex(startMillis)
            }
          }

          if (cloneStatus == CloneStatus.PROGRESS) {
            value = (progressIndicator.fraction * 100).toInt()
          }

          isIndeterminate = isProgressIndeterminate
        }

        val icon = recentProjectsManager.getProjectIcon(item.projectPath, true)
        when (cloneStatus) {
          CloneStatus.PROGRESS -> {
            projectCloneStatusPanel.isVisible = true
            projectCloneStatusPanel.isEnabled = true
            projectProgressLabel.text = taskInfo.actionTitle
            projectIconLabel.icon = icon
          }
          CloneStatus.FAILURE -> {
            projectCloneStatusPanel.isVisible = false
            projectCloneStatusPanel.isEnabled = false
            projectPathLabel.text = taskInfo.failedTitle
            projectIconLabel.icon = IconUtil.desaturate(icon)
          }
          CloneStatus.CANCEL -> {
            projectCloneStatusPanel.isVisible = false
            projectCloneStatusPanel.isEnabled = false
            projectPathLabel.text = taskInfo.canceledTitle
            projectIconLabel.icon = IconUtil.desaturate(icon)
          }
          else -> {}
        }

        toolTipText = taskInfo.actionTooltipText

        return this
      }

      companion object {
        private const val startMillis = 0L
      }
    }
  }

  companion object {
    private fun createActionEvent(tree: Tree, inputEvent: InputEvent? = null): AnActionEvent {
      val dataContext = DataManager.getInstance().getDataContext(tree)
      val actionPlace =
        if (UIUtil.uiParents(tree, true).filter(FlatWelcomeFrame::class.java).isEmpty) ActionPlaces.POPUP
        else ActionPlaces.WELCOME_SCREEN

      return if (inputEvent == null) AnActionEvent.createFromDataContext(actionPlace, null, dataContext)
      else AnActionEvent.createFromInputEvent(inputEvent, actionPlace, null, dataContext)
    }

    private fun activateItem(tree: Tree) {
      val node = tree.lastSelectedPathComponent.castSafelyTo<DefaultMutableTreeNode>() ?: return
      val item = node.userObject.castSafelyTo<RecentProjectTreeItem>() ?: return
      when (item) {
        is RecentProjectItem -> {
          val actionEvent = createActionEvent(tree)
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
      val node = tree.lastSelectedPathComponent.castSafelyTo<DefaultMutableTreeNode>() ?: return
      val item = node.userObject as RecentProjectTreeItem
      val actionEvent = createActionEvent(tree)
      item.removeItem(actionEvent)
    }
  }
}