package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableCellState
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.attach.dialog.getComponentFont
import com.intellij.xdebugger.impl.ui.attach.dialog.getProcessName
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.border.Border


abstract class AttachTableCell(
  val columnKey: String,
  private val columnsLayout: AttachDialogColumnsLayout) {

  companion object {
    private val IGNORE_EXPAND_HANDLER_GAP = JBUI.scale(10)
  }

  private var myLastKnownWidth = -1
  private var myLastKnownDisplayText = ""

  @Nls
  protected abstract fun getTextToDisplay(): String

  open fun getIcon(): Icon? = null

  open fun getTag(): Any? = null

  open fun getTextStartOffset(component: SimpleColoredComponent): Int = 0

  open fun getTextAttributes(): SimpleTextAttributes = SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES

  fun getPresentation(component: SimpleColoredComponent, offset: Int = 0): Pair<String, String?> {
    val width = columnsLayout.getColumnWidth(columnKey) - getTextStartOffset(component) - IGNORE_EXPAND_HANDLER_GAP
    val text = getTextToDisplay()
    if (myLastKnownWidth != width) {
      myLastKnownDisplayText = getProcessName(text, component.getFontMetrics(getComponentFont(component)), width - offset)
    }
    return Pair(myLastKnownDisplayText,
                if (text != myLastKnownDisplayText)
                  HtmlChunk.html()
                    .children(
                      HtmlChunk.div("font-weight:bold;").child(HtmlChunk.text(columnsLayout.getColumnName(columnKey))),
                      HtmlChunk.br(),
                      HtmlChunk.text(text)
                    ).toString()
                else
                  null)
  }
}

private class AttachCellState : TableCellState() {
  override fun getBorder(isSelected: Boolean, hasFocus: Boolean): Border? = null
}

internal class AttachTableCellRenderer : ColoredTableCellRenderer() {
  init {
    cellState = AttachCellState()
  }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value !is AttachTableCell) return
    val (presentation, tooltip) = value.getPresentation(this)
    val valueIcon = value.getIcon()
    if (valueIcon == null) {
      append("", SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES, value.getTextStartOffset(this), SwingConstants.LEFT)
    }
    append(presentation, value.getTextAttributes(), value.getTag())
    toolTipText = tooltip
    icon = valueIcon
  }
}
