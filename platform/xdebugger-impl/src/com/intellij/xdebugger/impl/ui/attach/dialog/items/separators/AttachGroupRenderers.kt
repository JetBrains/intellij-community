package com.intellij.xdebugger.impl.ui.attach.dialog.items.separators

import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.tryCastValue
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class AttachGroupFirstColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): JComponent {
    val groupNode = tryCastValue<AttachDialogGroupNode>(value) ?: throw IllegalStateException(
      "Expected the element of type ${AttachDialogGroupNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderFirstColumnSeparator(groupNode.message, groupNode.isFirstGroup)
  }
}

internal class AttachGroupColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val groupNode = tryCastValue<AttachDialogGroupNode>(value) ?: throw IllegalStateException(
      "Expected the element of type ${AttachDialogGroupNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderColumnSeparator(groupNode.isFirstGroup)
  }
}

internal class AttachGroupLastColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val groupNode = tryCastValue<AttachDialogGroupNode>(value) ?: throw IllegalStateException(
      "Expected the element of type ${AttachDialogGroupNode::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderLastColumnSeparator(groupNode.isFirstGroup)
  }
}