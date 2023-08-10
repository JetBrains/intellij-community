// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.validation

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.TextTransferable
import java.awt.datatransfer.Transferable
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * @author Alexander Lobas
 */
class MyPanel {
  public fun createTree(): JComponent {
    val root = DefaultMutableTreeNode("Main")
    val child1 = DefaultMutableTreeNode("Child1")
    child1.add(DefaultMutableTreeNode("Child1_1"))
    child1.add(DefaultMutableTreeNode("Child1_2"))
    child1.add(DefaultMutableTreeNode("Child1_3"))
    root.add(child1)
    root.add(DefaultMutableTreeNode("Child2"))
    root.add(DefaultMutableTreeNode("Child3"))

    //val tree = Tree(root)
    val tree = JTree(root)
    tree.dragEnabled = true
    //tree.dropMode = DropMode.ON_OR_INSERT
    /*tree.cellRenderer = object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        val node = value as? DefaultMutableTreeNode ?: return
        append(node.userObject.toString())
      }
    }*/
    tree.cellRenderer = DefaultTreeCellRenderer()
    tree.transferHandler = object : TransferHandler() {
      override fun getSourceActions(c: JComponent): Int = MOVE
      override fun canImport(support: TransferSupport?): Boolean {
        return true
      }

      override fun createTransferable(c: JComponent?): Transferable {
        val myTree = c as JTree
        val path = myTree.selectionPaths?.firstOrNull()
        val node = path?.lastPathComponent as? DefaultMutableTreeNode
        return TextTransferable(node?.userObject as? String)
      }
    }
    return tree
  }
}

fun main() {
  val frame = JFrame("Fooo")
  frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
  frame.contentPane = MyPanel().createTree()
  frame.setBounds(100, 100, 480, 320)
  frame.isVisible = true
}