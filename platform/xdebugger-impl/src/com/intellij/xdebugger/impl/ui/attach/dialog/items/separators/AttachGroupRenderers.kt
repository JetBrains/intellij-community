package com.intellij.xdebugger.impl.ui.attach.dialog.items.separators

import com.intellij.xdebugger.impl.ui.attach.dialog.items.list.AttachToProcessListGroupBase
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
    if (value !is AttachToProcessListGroupBase) throw IllegalStateException(
      "Expected the element of type ${AttachToProcessListGroupBase::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderFirstColumnSeparator(value.groupName, value.isFirstGroup)
  }
}

internal class AttachGroupColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    if (value !is AttachToProcessListGroupBase) throw IllegalStateException(
      "Expected the element of type ${AttachToProcessListGroupBase::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderColumnSeparator(value.isFirstGroup)
  }
}

internal class AttachGroupLastColumnRenderer : TableCellRenderer {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    if (value !is AttachToProcessListGroupBase) throw IllegalStateException(
      "Expected the element of type ${AttachToProcessListGroupBase::class.java.simpleName} but received ${value?.javaClass?.simpleName}")
    return TableGroupHeaderLastColumnSeparator(value.isFirstGroup)
  }
}