// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.treetable.TreeTableTree
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree

internal class AttachTreeNodeWrapper(
  val node: AttachDialogElementNode,
  private val filters: AttachToProcessElementsFilters,
  private val columnsLayout: AttachDialogColumnsLayout,
  indentLevel: Int = 0) {

  private val children = mutableListOf<AttachTreeNodeWrapper>()
  private var parent: AttachTreeNodeWrapper? = null
  private var myIndentLevel: Int = indentLevel


  private val elementRenderer = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      val attachTreeProcessNode = value as? AttachDialogProcessNode ?: throw IllegalStateException(
        "value of type ${value?.javaClass?.simpleName} should not be passed here")
      val cell = columnsLayout.createCell(0, attachTreeProcessNode, filters, true)
      val (text, tooltip) = cell.getPresentation(this, (getIndentLevel() + 1) * JBUI.scale(18))
      val table = (tree as? TreeTableTree)?.treeTable as? JBTable ?: throw IllegalStateException("can't get parent table")
      this.append(text, modifyAttributes(cell.getTextAttributes(), table, row))
      this.toolTipText = tooltip
    }

    private fun modifyAttributes(attributes: SimpleTextAttributes, tree: JBTable, row: Int): SimpleTextAttributes {
      //We should not trust the selected value as the TreeTableTree
      // is inserted into table and usually has wrong selection information
      val isTableRowSelected = tree.isRowSelected(row)
      return if (!isTableRowSelected) attributes else SimpleTextAttributes(attributes.style, RenderingUtil.getSelectionForeground(tree))
    }
  }


  fun addChild(child: AttachTreeNodeWrapper) {
    children.add(child)
    child.parent = this
    child.updateIndentLevel(myIndentLevel + 1)
  }

  fun getChildNodes(): List<AttachTreeNodeWrapper> = children

  fun getParent(): AttachTreeNodeWrapper? = parent

  fun getIndentLevel(): Int = myIndentLevel

  private fun updateIndentLevel(newIndentLevel: Int) {
    myIndentLevel = newIndentLevel
    for (child in children) {
      child.updateIndentLevel(newIndentLevel + 1)
    }
  }


  fun getTreeCellRendererComponent(tree: JTree?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean): Component {
    return when (node) {
      is AttachDialogGroupNode -> JLabel(node.message)
      is AttachDialogProcessNode -> elementRenderer.getTreeCellRendererComponent(tree, node, selected, expanded, leaf, row, hasFocus)
      is AttachTreeRootNode -> JLabel()
      else -> throw IllegalStateException("Unexpected node type: ${node.javaClass.simpleName}")
    }
  }

  fun getValueAtColumn(column: Int): Any {
    if (column == 0) return this
    return node.getValueAtColumn(column)
  }

  override fun toString(): String = "($node)"
}