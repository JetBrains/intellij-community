// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.ProjectDetector
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel.FilePathChecker
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectChange
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SmartExpander
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.IconUtil
import com.intellij.util.PathUtil
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

internal object RecentProjectPanelComponentFactory {
  private const val UPDATE_INTERVAL = 1_000 // 1 sec

  @JvmStatic
  fun createComponent(parentDisposable: Disposable): RecentProjectFilteringTree {
    ProjectDetector.runDetectors {} // Run detectors that will add projects to the RecentProjectsManagerBase

    val tree = Tree()
    val filteringTree = RecentProjectFilteringTree(tree).apply {
      installSearchField()
    }

    tree.apply {
      val filePathChecker = createFilePathChecker(filteringTree)
      Disposer.register(parentDisposable, filePathChecker)

      setEmptyState(IdeBundle.message("empty.text.no.project.open.yet"))
      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) { activateItem(this) }
      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)) { removeItem(this) }
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
      rowHeight = 0 // Fix tree renderer size on macOS

      setExpandableItemsEnabled(false)
    }

    ApplicationManager.getApplication().messageBus.connect(parentDisposable).apply {
      subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, RecentProjectsChange { filteringTree.searchModel.updateStructure() })
      subscribe(CloneableProjectsService.TOPIC, object : CloneProjectChange {
        override fun change() = filteringTree.searchModel.updateStructure()
      })
    }

    val updateQueue = MergingUpdateQueue("Welcome screen UI updater", UPDATE_INTERVAL, true, null,
                                         parentDisposable, tree, Alarm.ThreadToUse.SWING_THREAD)
    updateQueue.queue(Update.create(filteringTree, Runnable { updateProgressBars(updateQueue, filteringTree) }))

    return filteringTree
  }

  private fun activateItem(tree: Tree) {
    val node = tree.lastSelectedPathComponent.castSafelyTo<DefaultMutableTreeNode>() ?: return
    val item = node.userObject.castSafelyTo<RecentProjectItem>() ?: return
    val actionEvent = createActionEvent(tree)
    item.openProject(actionEvent)
  }

  private fun removeItem(tree: Tree) {
    val node = tree.lastSelectedPathComponent.castSafelyTo<DefaultMutableTreeNode>() ?: return
    val item = node.userObject as RecentProjectTreeItem
    val actionEvent = createActionEvent(tree)
    item.removeItem(actionEvent)
  }

  private fun createActionEvent(tree: Tree, inputEvent: InputEvent? = null): AnActionEvent {
    val dataContext = DataManager.getInstance().getDataContext(tree)
    val actionPlace =
      if (UIUtil.uiParents(tree, true).filter(FlatWelcomeFrame::class.java).isEmpty) ActionPlaces.POPUP
      else ActionPlaces.WELCOME_SCREEN

    return if (inputEvent == null) AnActionEvent.createFromDataContext(actionPlace, null, dataContext)
    else AnActionEvent.createFromInputEvent(inputEvent, actionPlace, null, dataContext)
  }

  private fun updateProgressBars(updateQueue: MergingUpdateQueue, filteringTree: RecentProjectFilteringTree) {
    filteringTree.component.repaint()
    updateQueue.queue(Update.create(filteringTree, Runnable { updateProgressBars(updateQueue, filteringTree) }))
  }

  private fun createFilePathChecker(filteringTree: RecentProjectFilteringTree): FilePathChecker {
    val recentProjectTreeItems: List<RecentProjectTreeItem> = RecentProjectListActionProvider.getInstance().collectProjects()
    val recentProjects = mutableListOf<RecentProjectItem>()
    for (item in recentProjectTreeItems) {
      when (item) {
        is RecentProjectItem -> recentProjects.add(item)
        is ProjectsGroupItem -> recentProjects.addAll(item.children)
        else -> {}
      }
    }

    val treeUpdater = Runnable { filteringTree.searchModel.updateStructure() }

    return FilePathChecker(treeUpdater, recentProjects.map { it.projectPath })
  }

  private class ProjectActionMouseListener(private val tree: Tree) : PopupHandler() {
    override fun mouseReleased(mouseEvent: MouseEvent) {
      super.mouseReleased(mouseEvent)
      if (mouseEvent.isConsumed) return

      val point = mouseEvent.point
      if (intersectWithActionIcon(point)) {
        val node = tree.lastSelectedPathComponent as DefaultMutableTreeNode
        when (val treeItem = node.userObject as RecentProjectTreeItem) {
          is CloneableProjectItem -> CloneableProjectsService.getInstance().removeCloneProject(treeItem.progressIndicator)
          else -> invokePopup(mouseEvent.component, point.x, point.y)
        }
      }
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
          is ProjectsGroupItem -> if (tree.isExpanded(treePath)) tree.collapsePath(treePath) else tree.expandPath(treePath)
          is RecentProjectItem -> project.openProject(createActionEvent(tree))
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
    private val recentProjectsManager: RecentProjectsManagerBase
      get() = RecentProjectsManagerBase.instanceEx

    // Recent project component
    private val recentProjectNameLabel = JLabel()
    private val recentProjectPathLabel = JLabel().apply {
      foreground = UIUtil.getInactiveTextColor()
    }
    private val recentProjectIconLabel = JLabel().apply {
      border = JBUI.Borders.empty(8, 0, 0, 8)
      horizontalAlignment = SwingConstants.LEFT
      verticalAlignment = SwingConstants.TOP
    }
    private val recentProjectActions = JLabel().apply {
      border = JBUI.Borders.empty(0, 0, 0, 10)
      icon = IconUtil.toSize(AllIcons.Ide.Notification.Gear,
                             ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth().toInt(),
                             ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight().toInt())
    }
    private val recentProjectNamePanel = JBUI.Panels.simplePanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(4)

      add(recentProjectNameLabel, BorderLayout.NORTH)
      add(recentProjectPathLabel, BorderLayout.SOUTH)
    }
    private val recentProjectComponent = JBUI.Panels.simplePanel().apply {
      border = JBUI.Borders.empty(4)

      add(recentProjectNamePanel, BorderLayout.CENTER)
      add(recentProjectIconLabel, BorderLayout.WEST)
      add(recentProjectActions, BorderLayout.EAST)
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

    // Cloneable project component
    private val cloneableProjectNameLabel = JLabel().apply {
      foreground = UIUtil.getInactiveTextColor()
    }
    private val cloneableProjectPathLabel = JLabel().apply {
      foreground = UIUtil.getInactiveTextColor()
    }
    private val cloneableProjectNamePanel = JBUI.Panels.simplePanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(4)

      add(cloneableProjectNameLabel, BorderLayout.NORTH)
      add(cloneableProjectPathLabel, BorderLayout.SOUTH)
    }
    private val cloneableProjectIconLabel = JLabel().apply {
      border = JBUI.Borders.empty(8, 0, 0, 8)
      horizontalAlignment = SwingConstants.LEFT
      verticalAlignment = SwingConstants.TOP
    }
    private val cloneableProjectCancelButton = JLabel().apply {
      icon = AllIcons.Actions.DeleteTag
      border = JBUI.Borders.empty(0, 0, 0, 17)
    }
    private val cloneableProjectProgressBar = JProgressBar().apply {
      isOpaque = false

      border = JBUI.Borders.empty(4)
    }
    private val cloneableProjectProgressLabel = JLabel().apply {
      text = IdeBundle.message("welcome.screen.projects.cloning")
      foreground = UIUtil.getInactiveTextColor()
    }
    private val cloneableProjectCloneStatusPanel = JBUI.Panels.simplePanel().apply {
      isOpaque = false

      add(cloneableProjectProgressLabel, BorderLayout.WEST)
      add(cloneableProjectProgressBar, BorderLayout.CENTER)
      add(cloneableProjectCancelButton, BorderLayout.EAST)
    }
    private val cloneableProjectComponent = JBUI.Panels.simplePanel().apply {
      isOpaque = false

      add(cloneableProjectNamePanel, BorderLayout.CENTER)
      add(cloneableProjectIconLabel, BorderLayout.WEST)
      add(cloneableProjectCloneStatusPanel, BorderLayout.EAST)
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
        is CloneableProjectItem -> createCloneableProjectComponent(item)
      }
    }

    private fun createReopenProjectComponent(item: RecentProjectItem, isHovered: Boolean): JComponent = recentProjectComponent.apply {
      val isProjectPathValid = isProjectPathValid(item.projectPath)

      recentProjectNameLabel.apply {
        text = item.displayName
        foreground = if (isProjectPathValid) UIUtil.getListForeground() else UIUtil.getInactiveTextColor()
      }
      recentProjectPathLabel.apply {
        text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
      }
      recentProjectIconLabel.apply {
        icon = recentProjectsManager.getProjectIcon(item.projectPath, true)
        disabledIcon = IconUtil.desaturate(icon)
        isEnabled = isProjectPathValid
      }
      recentProjectActions.isVisible = isHovered

      val toolTipPath = PathUtil.toSystemDependentName(item.projectPath)
      toolTipText = if (isProjectPathValid) toolTipPath else "$toolTipPath ${IdeBundle.message("recent.project.unavailable")}"

      AccessibleContextUtil.setCombinedName(this, recentProjectNameLabel, "-", recentProjectPathLabel) // NON-NLS
      AccessibleContextUtil.setCombinedDescription(this, recentProjectNameLabel, "-", recentProjectPathLabel) // NON-NLS
    }

    private fun createProjectGroupComponent(item: ProjectsGroupItem, isHovered: Boolean): JComponent {
      projectGroupLabel.apply {
        clear()
        append(item.displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) // NON-NLS
      }
      projectGroupActions.isVisible = isHovered

      return projectGroupComponent.apply {
        AccessibleContextUtil.setName(this, projectGroupLabel) // NON-NLS
        AccessibleContextUtil.setDescription(this, projectGroupLabel) // NON-NLS
      }
    }

    private fun createCloneableProjectComponent(item: CloneableProjectItem): JComponent {
      cloneableProjectNameLabel.apply {
        text = item.displayName() // NON-NLS
      }
      cloneableProjectPathLabel.apply {
        text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(item.projectPath), false)
      }
      cloneableProjectIconLabel.apply {
        icon = IconUtil.desaturate(recentProjectsManager.getProjectIcon(item.projectPath, true))
      }
      cloneableProjectProgressBar.apply {
        val progressIndicator = item.progressIndicator
        if (progressIndicator.isCanceled) {
          cloneableProjectCloneStatusPanel.isVisible = false
          cloneableProjectPathLabel.text = IdeBundle.message("welcome.screen.projects.clone.failed")
          return@apply
        }

        value = (item.progressIndicator.fraction * 100).toInt()
      }

      return cloneableProjectComponent.apply {
        toolTipText = item.progressIndicator.text2
      }
    }
  }
}