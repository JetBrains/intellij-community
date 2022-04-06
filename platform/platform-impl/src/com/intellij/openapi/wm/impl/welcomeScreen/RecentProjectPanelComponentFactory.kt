// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel.FilePathChecker
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RootItem
import com.intellij.ui.*
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

internal class RecentProjectPanelComponentFactory(
  private val parentDisposable: Disposable
) {
  private val tree = Tree().apply {
    val filePathChecker = createFilePathChecker()
    Disposer.register(parentDisposable, filePathChecker)

    setEmptyState(IdeBundle.message("empty.text.no.project.open.yet"))
    addMouseListener(ProjectActionMouseListener(this))
    putClientProperty(Control.Painter.KEY, Control.Painter.LEAF_WITHOUT_INDENT)
    putClientProperty(
      RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
      Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), true, true) }
    )

    SmartExpander.installOn(this)
    TreeHoverListener.DEFAULT.addTo(this)

    isRootVisible = false
    cellRenderer = ProjectActionRenderer(filePathChecker::isValid)
  }

  private val filteringTree = ProjectActionFilteringTree(tree).apply {
    installSearchField()
  }

  init {
    val defaultProject = DefaultProjectFactory.getInstance().defaultProject
    defaultProject.messageBus
      .connect(parentDisposable)
      .subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, RecentProjectsChange { filteringTree.searchModel.updateStructure() })
  }

  fun createComponent(): ProjectActionFilteringTree {
    return filteringTree
  }

  private fun createFilePathChecker(): FilePathChecker {
    val recentProjectTreeItems: List<RecentProjectTreeItem> = RecentProjectListActionProvider.getInstance().collectProjects()
    val recentProjects = recentProjectTreeItems.filterIsInstance<ProjectsGroupItem>()
      .flatMap { item -> item.children }
      .toMutableList()
      .apply { addAll(recentProjectTreeItems.filterIsInstance<RecentProjectItem>()) }
      .map { it.displayName() }

    val treeUpdater = Runnable { filteringTree.searchModel.updateStructure() }

    return FilePathChecker(treeUpdater, recentProjects)
  }

  class ProjectActionFilteringTree(tree: Tree) : FilteringTree<DefaultMutableTreeNode, RecentProjectTreeItem>(
    ProjectManager.getInstance().defaultProject,
    tree,
    DefaultMutableTreeNode(RootItem)
  ) {
    override fun getNodeClass() = DefaultMutableTreeNode::class.java

    override fun getText(item: RecentProjectTreeItem?): String = item?.displayName().orEmpty()

    override fun getChildren(item: RecentProjectTreeItem): Iterable<RecentProjectTreeItem> = item.children()

    override fun createNode(item: RecentProjectTreeItem): DefaultMutableTreeNode = DefaultMutableTreeNode(item)

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
        }
      }
    }

    override fun expandTreeOnSearchUpdateComplete(pattern: String?) {
      TreeUtil.expandAll(tree)
    }

    override fun useIdentityHashing(): Boolean = false
  }

  private class ProjectActionMouseListener(private val tree: Tree) : PopupHandler() {
    override fun mouseReleased(mouseEvent: MouseEvent) {
      super.mouseReleased(mouseEvent)
      if (mouseEvent.isConsumed) return

      val point = mouseEvent.point
      if (intersectWithActionIcon(point)) invokePopup(mouseEvent.component, point.x, point.y)
      else activateItem(mouseEvent)

      mouseEvent.consume()
    }

    override fun invokePopup(component: Component, x: Int, y: Int) {
      val group = ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup") as ActionGroup
      ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).component.show(component, x, y)
    }

    private fun intersectWithActionIcon(point: Point): Boolean {
      val row = tree.getClosestRowForLocation(point.x, point.y)
      return row != -1 && getCloseIconRect(row).contains(point)
    }

    private fun activateItem(mouseEvent: MouseEvent) {
      val treePath = tree.selectionPath ?: return
      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        val treeNode = treePath.lastPathComponent as DefaultMutableTreeNode
        when (val project = treeNode.userObject) {
          is RootItem -> {} // Specific for RootItem (not visible in tree)
          is ProjectsGroupItem -> {
            if (tree.isExpanded(treePath)) tree.collapsePath(treePath) else tree.expandPath(treePath)
          }
          is RecentProjectItem -> {
            val dataContext = DataManager.getInstance().getDataContext(tree)
            val anActionEvent = AnActionEvent.createFromInputEvent(mouseEvent, ActionPlaces.WELCOME_SCREEN, null, dataContext)
            project.openProject(anActionEvent)
          }
        }
      }
    }

    private fun getCloseIconRect(row: Int): Rectangle {
      val bounds = tree.getRowBounds(row)
      val icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                                 ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                                 ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())

      // Use `tree.bound` because the renderer's border is not full width
      return Rectangle(tree.bounds.width - icon.iconWidth - JBUIScale.scale(10),
                       bounds.y + (bounds.height - icon.iconHeight) / 2,
                       icon.iconWidth, icon.iconHeight)
    }
  }

  private class ProjectActionRenderer(
    private val isProjectPathValid: (String) -> Boolean
  ) : TreeCellRenderer {
    private val recentProjectsManager = RecentProjectsManagerBase.instanceEx

    // Recent project component
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
    private val namePanel = JBUI.Panels.simplePanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(4)

      add(projectNameLabel, BorderLayout.NORTH)
      add(projectPathLabel, BorderLayout.SOUTH)
    }
    private val recentProjectComponent = JBUI.Panels.simplePanel().apply {
      border = JBUI.Borders.empty(4)

      add(namePanel, BorderLayout.CENTER)
      add(projectIconLabel, BorderLayout.WEST)
      add(projectActions, BorderLayout.EAST)
    }

    // Project group component
    private val projectGroupLabel = SimpleColoredComponent().apply {
      isOpaque = false
      border = JBUI.Borders.empty(4)
    }
    private val projectGroupActions = JLabel().apply {
      border = JBUI.Borders.empty(0, 0, 0, 14)
      icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                             ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                             ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())
    }
    private val projectGroupComponent = JBUI.Panels.simplePanel().apply {
      isOpaque = false

      add(projectGroupLabel, BorderLayout.WEST)
      add(projectGroupActions, BorderLayout.EAST)
    }

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
      val isHovered = TreeHoverListener.getHoveredRow(tree) == row

      return when (val item = (value as DefaultMutableTreeNode).userObject as RecentProjectTreeItem) {
        is RootItem -> JBUI.Panels.simplePanel()
        is ProjectsGroupItem -> createProjectGroupComponent(item, isHovered)
        is RecentProjectItem -> createReopenProjectComponent(item, isHovered)
      }
    }

    private fun createReopenProjectComponent(item: RecentProjectItem, isHovered: Boolean): JComponent = recentProjectComponent.apply {
      val isProjectPathValid = isProjectPathValid(item.projectPath)

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
      projectActions.isVisible = isHovered
    }

    private fun createProjectGroupComponent(item: ProjectsGroupItem, isHovered: Boolean): JComponent {
      projectGroupLabel.apply {
        clear()
        append(item.displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) // NON-NLS
      }
      projectGroupActions.isVisible = isHovered

      return projectGroupComponent
    }
  }
}