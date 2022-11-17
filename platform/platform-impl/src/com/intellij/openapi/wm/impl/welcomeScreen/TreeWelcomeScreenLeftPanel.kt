// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WelcomeScreenLeftPanel
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenEventCollector.Companion.logWelcomeScreenShown
import com.intellij.ui.UIBundle
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Component
import java.awt.Graphics
import java.util.*
import java.util.function.Consumer
import javax.accessibility.Accessible
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.*
import kotlin.collections.ArrayDeque

class TreeWelcomeScreenLeftPanel : WelcomeScreenLeftPanel {
  private val root = DefaultMutableTreeNode()
  private val treeModel = DefaultTreeModel(root)
  private val tree: JTree = Tree(treeModel)
  private var wasLoaded = false
  private val queue = ArrayDeque<() -> Unit>()

  init {
    TreeUtil.installActions(tree)

    tree.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true)
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    tree.isRootVisible = false
    tree.background = WelcomeScreenUIManager.getMainTabListBackground()
    tree.border = JBUI.Borders.emptyLeft(16)
    tree.cellRenderer = MyCellRenderer()
    tree.rowHeight = 0

    tree.accessibleContext.accessibleName = UIBundle.message("welcome.screen.welcome.screen.categories.accessible.name")
  }

  override fun addRootTab(tab: WelcomeScreenTab) {
    addTab(root, tab)
  }

  private fun addTab(parent: DefaultMutableTreeNode, tab: WelcomeScreenTab) {
    val child = DefaultMutableTreeNode(tab)
    parent.add(child)
    tab.childTabs.forEach { addTab(child, it) }
  }

  override fun addSelectionListener(disposable: Disposable, action: Consumer<in WelcomeScreenTab>) {
    val tsl = TreeSelectionListener { e ->
      val tab = TreeUtil.getUserObject(WelcomeScreenTab::class.java, e.path.lastPathComponent) ?: return@TreeSelectionListener
      if (!wasLoaded) {
        queue.add {
          action.accept(tab)
        }
        return@TreeSelectionListener
      }
      action.accept(tab)
    }
    tree.addTreeSelectionListener(tsl)
    Disposer.register(disposable, Disposable {
      tree.removeTreeSelectionListener(tsl)
    })
  }

  override fun selectTab(tab: WelcomeScreenTab): Boolean {
    val targetNode = TreeUtil.treeNodeTraverser(root).traverse(TreeTraversal.POST_ORDER_DFS).find { node: TreeNode? ->
      if (node is DefaultMutableTreeNode) {
        val currentTab = node.userObject
        if (currentTab === tab) {
          return@find true
        }
      }
      false
    }

    if (targetNode == null) return false

    TreeUtil.selectNode(tree, targetNode)

    return true
  }

  override fun getTabByIndex(idx: Int): WelcomeScreenTab? {
    val tab = tree.getPathForRow(idx).lastPathComponent as? DefaultMutableTreeNode ?: return null

    return tab.userObject as? DefaultWelcomeScreenTab
  }

  override fun removeAllTabs() {
    root.removeAllChildren()
  }

  override fun init() {
    //select and install focused component
    if (root.childCount > 0) {
      val firstTabNode = root.firstChild as DefaultMutableTreeNode
      val firstTab = TreeUtil.getUserObject(WelcomeScreenTab::class.java, firstTabNode)
      TreeUtil.selectNode(tree, firstTabNode)
      TreeUtil.expandAll(tree)
      val firstShownPanel = firstTab!!.associatedComponent
      UiNotifyConnector.doWhenFirstShown(firstShownPanel) {
        val preferred = IdeFocusTraversalPolicy.getPreferredFocusedComponent(firstShownPanel)
        IdeFocusManager.getGlobalInstance().requestFocus(Objects.requireNonNullElse(preferred, tree), true)
        logWelcomeScreenShown()
      }
    }
    wasLoaded = true
    queue.forEach { it() }
  }

  override fun getComponent(): JComponent {
    return tree
  }
}

private class MyCellRenderer : TreeCellRenderer {
  override fun getTreeCellRendererComponent(tree: JTree,
                                            value: Any,
                                            isSelected: Boolean,
                                            isExpanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            cellHasFocus: Boolean): Component {
    val tab = TreeUtil.getUserObject(WelcomeScreenTab::class.java, value)
    val keyComponent = tab?.getKeyComponent(tree) ?: JLabel("")
    val wrappedPanel: JPanel = JBUI.Panels.simplePanel(keyComponent)
    UIUtil.setBackgroundRecursively(wrappedPanel, if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus)
    else WelcomeScreenUIManager.getMainTabListBackground())
    UIUtil.setForegroundRecursively(wrappedPanel, UIUtil.getListForeground(isSelected, cellHasFocus))
    if (tab is Accessible) {
      wrappedPanel.accessibleContext.accessibleName = (tab as Accessible).accessibleContext.accessibleName
    }
    return wrappedPanel
  }
}

private class MyControlPainter : Control.Painter {
  private val delegate = Control.Painter.DEFAULT
  override fun getRendererOffset(control: Control, depth: Int, leaf: Boolean): Int {
    return if (depth == 1) {
      if (leaf) {
        delegate.getRendererOffset(control, 0, leaf)
      }
      else delegate.getRendererOffset(control, 1, leaf)
    }
    else delegate.getRendererOffset(control, depth - 1, leaf)
  }

  override fun getControlOffset(control: Control, depth: Int, leaf: Boolean): Int {
    return if (depth == 1) {
      if (leaf) {
        delegate.getControlOffset(control, 0, leaf)
      }
      else delegate.getControlOffset(control, 1, leaf)
    }
    else delegate.getControlOffset(control, depth - 1, leaf)
  }

  override fun paint(c: Component,
                     g: Graphics,
                     x: Int,
                     y: Int,
                     width: Int,
                     height: Int,
                     control: Control,
                     depth: Int,
                     leaf: Boolean,
                     expanded: Boolean,
                     selected: Boolean) {
    delegate.paint(c, g, x, y, width, height, control, depth, leaf, expanded, selected)
  }
}