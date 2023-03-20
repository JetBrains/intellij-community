package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.ui.table.JBTable
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachSelectionIgnoredNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.tree.AttachTreeNodeWrapper
import java.awt.Rectangle
import javax.swing.ListSelectionModel
import javax.swing.table.TableModel

internal interface AttachNodeContainer<TNodeType> {
  fun getAttachNode(): TNodeType
}

class AttachToProcessTableSelectionModel private constructor(
  private val table: JBTable,
  private val initialSelectionModel: ListSelectionModel) : ListSelectionModel by initialSelectionModel {

  constructor(table: JBTable): this(table, table.selectionModel)

  init {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
  }

  override fun setSelectionInterval(index0: Int, index1: Int) {
    if (index0 >= 0 && index0 < table.rowCount && table.model.getValueAt<AttachDialogElementNode>(index0) is AttachSelectionIgnoredNode) {

      val currentIndex = minSelectionIndex
      if (currentIndex < 0 || currentIndex >= table.rowCount) {
        val next = getNextIndex(index0)
        setSelectionInterval(next, next)
        return
      }

      if (currentIndex == index0 - 1) {
        val newIndex = getNextIndex(index0)
        setSelectionInterval(newIndex, newIndex)
        return
      }

      if (currentIndex == index0 + 1) {
        val newIndex = getPreviousIndex(index0)
        setSelectionInterval(newIndex, newIndex)
        return
      }

      setSelectionInterval(-1, -1)
      return
    }
    initialSelectionModel.setSelectionInterval(index0, index1)
    scrollIfNeeded(index0)
  }

  override fun addSelectionInterval(index0: Int, index1: Int) {
    if (index0 >= 0 && index0 < table.rowCount && table.model.getValueAt<AttachDialogElementNode>(index0) is AttachSelectionIgnoredNode) {
      return
    }
    initialSelectionModel.addSelectionInterval(index0, index1)
    scrollIfNeeded(index0)
  }

  private fun scrollIfNeeded(index0: Int) {
    if (index0 >= 0 && index0 < table.rowCount) {
      val cellRect = table.getCellRect(index0, 0, true)
      val visibleRect = table.visibleRect
      if (visibleRect.isEmpty) return
      if (visibleRect.contains(cellRect)) return
      if (visibleRect.y + visibleRect.height < cellRect.y + cellRect.height) {
        table.scrollRectToVisible(Rectangle(
          cellRect.x,cellRect.y + cellRect.height, cellRect.width, 0))
      }
      else {
        table.scrollRectToVisible(Rectangle(
          cellRect.x, cellRect.y, cellRect.width, 0))
      }
    }
  }

  private fun getPreviousIndex(index0: Int): Int {
    var newIndex = index0 - 1
    while (newIndex >= 0 && table.model.getValueAt<AttachDialogElementNode>(newIndex) is AttachSelectionIgnoredNode) {
      newIndex--
    }
    return if (newIndex >= 0) newIndex else -1
  }

  private fun getNextIndex(index0: Int): Int {
    var newIndex = index0 + 1
    while (newIndex < table.rowCount && table.model.getValueAt<AttachDialogElementNode>(newIndex) is AttachSelectionIgnoredNode) {
      newIndex++
    }
    return if (newIndex < table.rowCount) newIndex else -1
  }
}

internal inline fun <reified TNodeType> TableModel.getValueAt(row: Int): TNodeType? {
  if (row < 0 || row >= rowCount) {
    return null
  }
  return tryCastValue<TNodeType>(getValueAt(row, 0))
}

internal inline fun <reified TNodeType> tryCastValue(value: Any?) = when (value) {
  is TNodeType -> value
  is AttachNodeContainer<*> -> value.getAttachNode() as? TNodeType
  is AttachTreeNodeWrapper -> value.node as? TNodeType
  else -> null
}

internal fun JBTable.focusFirst() {
  val currentIndex = selectedRow
  if (currentIndex >= 0 && currentIndex < model.rowCount) {
    return
  }

  if (model.rowCount > 0) {
    selectionModel.setSelectionInterval(0, 0)
  }
}

internal fun JBTable.refilterSaveSelection(filters: AttachToProcessElementsFilters, refilter: () -> Unit) {
  filters.clear()
  val previouslySelectedRow = selectedRow
  val selectedItem = if (previouslySelectedRow in 0 until rowCount)
                        model.getValueAt<AttachDialogElementNode>(previouslySelectedRow)
                      else
                        null
  refilter()

  var isFirstGroup = true
  for (rowNumber in 0 until rowCount) {
    val valueAtRow = model.getValueAt<AttachDialogElementNode>(rowNumber)
    if (valueAtRow is AttachDialogGroupNode) {
      valueAtRow.isFirstGroup = isFirstGroup
      isFirstGroup = false
    }
    if (selectedItem != null && valueAtRow != null && selectedItem == valueAtRow) {
      selectionModel.setSelectionInterval(rowNumber, rowNumber)
    }
  }

  focusFirst()
}