package com.intellij.xdebugger.impl.ui.attach.dialog.items.separators

import com.intellij.xdebugger.impl.ui.attach.dialog.items.tree.AttachTreeSeparatorNode
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.TableCellRenderer
import javax.swing.tree.TreeCellRenderer

internal class AttachTreeGroupFirstColumnRenderer : TableCellRenderer, TreeCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): JComponent {
    if (value !is AttachTreeSeparatorNode) throw IllegalStateException(
      "Expected the element of type ${AttachTreeSeparatorNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderFirstColumnSeparator(null, false)
  }

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: Any?,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): JComponent {
    if (value !is AttachTreeSeparatorNode) throw IllegalStateException(
      "Expected the element of type ${AttachTreeSeparatorNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderFirstColumnSeparator(null, false)
  }
}

internal class AttachTreeGroupColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    if (value !is AttachTreeSeparatorNode) throw IllegalStateException(
      "Expected the element of type ${AttachTreeSeparatorNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderColumnSeparator(false)
  }
}

internal class AttachTreeGroupLastColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    if (value !is AttachTreeSeparatorNode) throw IllegalStateException(
      "Expected the element of type ${AttachTreeSeparatorNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderLastColumnSeparator(false)
  }
}