// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel.FilePathChecker
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.Root
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.WelcomeScreenProjectItem
import com.intellij.ui.*
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingUtil
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

internal class RecentProjectPanelComponentFactory(
  private val parentDisposable: Disposable
) {
  private val recentProjects: List<WelcomeScreenProjectItem> = RecentProjectListActionProvider.getInstance().collectProjects()

  fun createComponent(): ProjectActionFilteringTree {
    val root = initializeTree(recentProjects)
    val tree = Tree(root).apply {
      val treeUpdater = Runnable {
        if (isShowing) {
          revalidate()
          repaint()
        }
      }
      val filePathChecker = FilePathChecker(treeUpdater, recentProjects.map { action -> action.name() })
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

    val filteringTree = ProjectActionFilteringTree(tree, root).apply {
      installSearchField()
    }

    return filteringTree
  }

  private fun initializeTree(welcomeScreenProjectItems: List<WelcomeScreenProjectItem>): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode(Root(welcomeScreenProjectItems))
    val treeModel = DefaultTreeModel(root)
    welcomeScreenProjectItems.map { project ->
      when (project) {
        is Root -> {}
        is RecentProjectGroupItem -> {
          val projectGroupNode = DefaultMutableTreeNode(project)
          treeModel.insertNodeInto(projectGroupNode, root, root.childCount)
          project.children.forEach { child ->
            treeModel.insertNodeInto(DefaultMutableTreeNode(child), projectGroupNode, projectGroupNode.childCount)
          }
        }
        is RecentProjectItem -> treeModel.insertNodeInto(DefaultMutableTreeNode(project), root, root.childCount)
      }
    }

    return root
  }

  class ProjectActionFilteringTree(
    tree: Tree,
    root: DefaultMutableTreeNode
  ) : FilteringTree<DefaultMutableTreeNode, WelcomeScreenProjectItem>(
    ProjectManager.getInstance().defaultProject, tree, root
  ) {
    override fun getNodeClass() = DefaultMutableTreeNode::class.java

    override fun getText(item: WelcomeScreenProjectItem?): String = item?.name().orEmpty()

    override fun getChildren(item: WelcomeScreenProjectItem): Iterable<WelcomeScreenProjectItem> = when (item) {
      is Root -> item.children
      is RecentProjectGroupItem -> item.children
      else -> emptyList()
    }

    override fun createNode(item: WelcomeScreenProjectItem): DefaultMutableTreeNode = DefaultMutableTreeNode(item)

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
  }

  private class ProjectActionMouseListener(private val tree: Tree) : MouseAdapter() {
    override fun mousePressed(mouseEvent: MouseEvent) {
      val treePath = tree.selectionPath ?: return
      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        mouseEvent.consume()
        val treeNode = treePath.lastPathComponent as DefaultMutableTreeNode
        when (val project = treeNode.userObject) {
          is Root -> {} // Specific for root (not visible in tree)
          is RecentProjectGroupItem -> {
            if (tree.isExpanded(treePath)) tree.collapsePath(treePath) else tree.expandPath(treePath)
            tree.removeSelectionRow(tree.getRowForPath(treePath))
          }
          is RecentProjectItem -> {
            val dataContext = DataManager.getInstance().getDataContext(tree)
            val anActionEvent = AnActionEvent.createFromInputEvent(mouseEvent, ActionPlaces.WELCOME_SCREEN, null, dataContext)
            project.openProject(anActionEvent)
          }
        }
      }
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
    private val namePanel = JBUI.Panels.simplePanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(4)
      add(projectNameLabel, BorderLayout.NORTH)
      add(projectPathLabel, BorderLayout.SOUTH)
    }
    private val recentProjectComponent = JBUI.Panels.simplePanel().apply {
      border = JBUI.Borders.empty(4)
      isOpaque = false

      add(namePanel, BorderLayout.CENTER)
      add(projectIconLabel, BorderLayout.WEST)
    }

    // Project group component
    private val projectGroupComponent = SimpleColoredComponent().apply {
      border = JBUI.Borders.empty(4)
    }

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
      return when (val item = (value as DefaultMutableTreeNode).userObject as WelcomeScreenProjectItem) {
        is Root -> JBUI.Panels.simplePanel()
        is RecentProjectGroupItem -> createProjectGroupComponent(item)
        is RecentProjectItem -> createReopenProjectComponent(item)
      }
    }

    private fun createReopenProjectComponent(item: RecentProjectItem): JComponent = recentProjectComponent.apply {
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
    }

    private fun createProjectGroupComponent(item: RecentProjectGroupItem): JComponent = projectGroupComponent.apply {
      clear()
      append(item.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) // NON-NLS
    }
  }
}