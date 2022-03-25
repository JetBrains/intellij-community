// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.*
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.SpeedSearchSupply
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
  private val recentProjectActions: List<AnAction> = RecentProjectListActionProvider.getInstance().getProjectActions()

  fun createComponent(): ProjectActionFilteringTree {
    val root = initializeTree(recentProjectActions)
    val tree = Tree(root).apply {
      isRootVisible = false
      cellRenderer = ProjectActionRenderer(::isProjectPathValid)

      setEmptyState(IdeBundle.message("empty.text.no.project.open.yet"))
      addMouseListener(ProjectActionMouseListener(this))
      putClientProperty(
        RenderingUtil.CUSTOM_SELECTION_BACKGROUND,
        Supplier { ListUiUtil.WithTallRow.background(JList<Any>(), true, true) } // TODO: fix JList
      )

      SmartExpander.installOn(this)
    }

    val filteringTree = ProjectActionFilteringTree(tree, root).apply {
      installSearchField()
    }

    return filteringTree
  }

  private fun initializeTree(recentProjectActions: List<AnAction>): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode(Root(recentProjectActions))
    val treeModel = DefaultTreeModel(root)
    recentProjectActions.map { projectAction ->
      when (projectAction) {
        is ReopenProjectAction -> treeModel.insertNodeInto(DefaultMutableTreeNode(projectAction), root, root.childCount)
        is ProjectGroupActionGroup -> {
          val projectGroupNode = DefaultMutableTreeNode(projectAction)
          treeModel.insertNodeInto(projectGroupNode, root, root.childCount)
          projectAction.getChildren(null).forEach { child ->
            treeModel.insertNodeInto(DefaultMutableTreeNode(child), projectGroupNode, projectGroupNode.childCount)
          }
        }
        else -> {} // TODO: fix (using sealed interface)
      }
    }

    return root
  }

  private fun isProjectPathValid(path: String): Boolean = true // TODO: implement

  class ProjectActionFilteringTree(tree: Tree, root: DefaultMutableTreeNode) : FilteringTree<DefaultMutableTreeNode, AnAction>(
    ProjectManager.getInstance().defaultProject, tree, root
  ) {
    override fun getNodeClass() = DefaultMutableTreeNode::class.java

    override fun getText(action: AnAction?): String? = when (action) {
      is ReopenProjectAction -> action.projectName
      is ProjectGroupActionGroup -> action.group.name
      else -> "" // TODO: fix (using sealed interface)
    }

    override fun getChildren(action: AnAction): Iterable<AnAction> = when (action) {
      is Root -> action.getChildren(null).toMutableList()
      is ProjectGroupActionGroup -> action.getChildren(null).toMutableList()
      else -> emptyList()
    }

    override fun createNode(action: AnAction): DefaultMutableTreeNode = DefaultMutableTreeNode(action)

    override fun createSpeedSearch(searchTextField: SearchTextField): SpeedSearchSupply = object : FilteringSpeedSearch(searchTextField) {}

    override fun installSearchField(): SearchTextField {
      return super.installSearchField().apply {
        isOpaque = false
        border = JBUI.Borders.empty()

        textEditor.apply {
          isOpaque = false;
          border = JBUI.Borders.empty();
          emptyText.text = IdeBundle.message("welcome.screen.search.projects.empty.text");
          accessibleContext.accessibleName = IdeBundle.message("welcome.screen.search.projects.empty.text");
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
      if (mouseEvent.clickCount == 2 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        mouseEvent.consume()
        val treeNode = treePath.lastPathComponent as DefaultMutableTreeNode
        val action = treeNode.userObject as AnAction
        val dataContext = DataManager.getInstance().getDataContext(tree)
        val anActionEvent = AnActionEvent.createFromInputEvent(mouseEvent, ActionPlaces.WELCOME_SCREEN, null, dataContext)
        action.actionPerformed(anActionEvent)
      }
    }
  }

  private class ProjectActionRenderer(
    private val isProjectPathValid: (String) -> Boolean
  ) : TreeCellRenderer {
    private val recentProjectsManager = RecentProjectsManagerBase.instanceEx

    override fun getTreeCellRendererComponent(
      tree: JTree, value: Any,
      selected: Boolean, expanded: Boolean,
      leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
      return when (val item = (value as DefaultMutableTreeNode).userObject) {
        is ReopenProjectAction -> createReopenProjectComponent(item)
        is ProjectGroupActionGroup -> createProjectGroupComponent(item)
        else -> JBUI.Panels.simplePanel()
      }
    }

    private fun createReopenProjectComponent(action: ReopenProjectAction): JComponent = JBUI.Panels.simplePanel().apply {
      border = JBUI.Borders.empty(4)
      isOpaque = false

      val isProjectPathValid = isProjectPathValid(action.projectPath)
      val projectNameLabel = JLabel().apply {
        text = action.projectNameToDisplay
        foreground = if (isProjectPathValid) UIUtil.getListForeground() else UIUtil.getInactiveTextColor()
      }
      val projectPathLabel = JLabel().apply {
        text = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(action.projectPath), false)
        foreground = UIUtil.getInactiveTextColor()
      }
      val projectIconLabel = JLabel().apply {
        icon = recentProjectsManager.getProjectIcon(action.projectPath, true)
        disabledIcon = IconUtil.desaturate(icon)
        border = JBUI.Borders.empty(8, 0, 0, 8)
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
        isEnabled = isProjectPathValid
      }
      val namePanel = JBUI.Panels.simplePanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty(4)
        add(projectNameLabel, BorderLayout.NORTH)
        add(projectPathLabel, BorderLayout.SOUTH)
      }

      add(namePanel, BorderLayout.CENTER)
      add(projectIconLabel, BorderLayout.WEST)
    }

    private fun createProjectGroupComponent(action: ProjectGroupActionGroup): JComponent = SimpleColoredComponent().apply {
      border = JBUI.Borders.empty(4)
      append(action.group.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
  }

  // The root node is required for the filtering tree
  private class Root(private val recentProjectActions: List<AnAction>) : DefaultActionGroup() {
    override fun getChildren(event: AnActionEvent?): Array<AnAction> = recentProjectActions.toTypedArray()
  }
}