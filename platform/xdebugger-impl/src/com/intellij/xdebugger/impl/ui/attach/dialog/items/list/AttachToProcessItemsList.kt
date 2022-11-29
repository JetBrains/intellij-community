package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.speedSearch.FilteringTableModel
import com.intellij.ui.table.JBTable
import com.intellij.util.application
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachItemsInfo
import com.intellij.xdebugger.impl.ui.attach.dialog.items.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayoutService
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.applyColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.tree.selectNext
import kotlinx.coroutines.ensureActive
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.ListSelectionListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableCellRenderer
import kotlin.coroutines.coroutineContext

internal class AttachToProcessItemsList(itemNodes: List<AttachDialogElementNode>,
                                        private val filters: AttachToProcessElementsFilters,
                                        columnsLayout: AttachDialogColumnsLayout,
                                        val state: AttachDialogState) :
  JBTable(FilteringTableModel(AttachToProcessTableModel(itemNodes, columnsLayout.getColumnsCount()), Any::class.java),
          AttachToProcessListColumnModel(columnsLayout)), AttachToProcessItemsListBase {

  init {
    setShowGrid(false)
    intercellSpacing = Dimension(0, 0)

    getFilteringModel().setFilter {
      val node = tryCastValue<AttachDialogElementNode>(it) ?: return@setFilter true
      return@setFilter filters.matches(node)
    }

    setSelectionModel(AttachToProcessTableSelectionModel(this))

    state.selectedDebuggersFilter.afterChange {
      refilterSaveSelection()
    }

    setEmptyState(XDebuggerBundle.message("xdebugger.attach.popup.emptyText"))

    getTableHeader().reorderingAllowed = false

    focusFirst()
    resetDefaultFocusTraversalKeys()
    installSelectionOnFocus()
    installRowsHeightUpdater()
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    val element = model.getValueAt<AttachDialogElementNode>(row) ?: throw IllegalStateException("Should not be null")
    return element.getRenderer(column) ?: super.getCellRenderer(row, column)
  }

  override fun updateFilter(searchFilterValue: String) {
    filters.updatePattern(searchFilterValue)
    refilterSaveSelection()
  }

  override fun getFocusedComponent(): JComponent = this

  override fun addSelectionListener(disposable: Disposable, listenerAction: (AttachDialogElementNode?) -> Unit) {
    val listener = ListSelectionListener {
      listenerAction(getSelectedItem())
    }
    selectionModel.addListSelectionListener(listener)
    Disposer.register(disposable) { selectionModel.removeListSelectionListener(listener) }
  }

  override fun getSelectedItem(): AttachDialogElementNode? {
    return model.getValueAt<AttachDialogElementNode>(selectedRow)
  }

  override fun selectNextItem() {
    selectionModel.selectNext()
  }

  private fun getFilteringModel(): FilteringTableModel<*> = model as FilteringTableModel<*>

  private fun refilterSaveSelection() {
    refilterSaveSelection(filters) {
      getFilteringModel().refilter()
      updateRowsHeight()
    }
  }
}

class AttachToProcessListColumnModel(columnsLayout: AttachDialogColumnsLayout) : DefaultTableColumnModel() {
  init {
    applyColumnsLayout(columnsLayout)
  }
}

internal class AttachToProcessTableModel(private val itemNodes: List<AttachDialogElementNode>, private val columnsCount: Int) : AbstractTableModel() {

  override fun getRowCount(): Int = itemNodes.size

  override fun getColumnCount(): Int = columnsCount

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val value = itemNodes[rowIndex] as? AttachDialogElementNode ?:
    throw IllegalStateException("The value on row $rowIndex is not an instance of ${AttachDialogElementNode::class.java.simpleName}")

    return value.getValueAtColumn(columnIndex)
  }
}

internal suspend fun buildList(itemsInfo: AttachItemsInfo, dialogState: AttachDialogState): AttachToProcessItemsList {

  val filters = AttachToProcessElementsFilters(dialogState.selectedDebuggersFilter)

  val columnsLayout = application.getService(AttachDialogColumnsLayoutService::class.java).getColumnsLayout()

  val itemNodes = mutableListOf<AttachDialogElementNode>()
  val recentItems = itemsInfo.recentItems
  if (recentItems.any()) {
    val recentItemNodes = recentItems.map { AttachDialogProcessNode(it, filters, columnsLayout) }
    val recentGroup = AttachDialogGroupNode(XDebuggerBundle.message("xdebugger.attach.dialog.recently.attached.message"), columnsLayout, recentItemNodes).apply { isFirstGroup = true }
    itemNodes.add(recentGroup)
    itemNodes.addAll(recentItemNodes)
  }

  val recentProcesses = recentItems.map { it.processInfo }.toSet()

  val allItems = mutableListOf<AttachDialogProcessNode>()

  for (item in itemsInfo.processItems.filter { it.debuggers.any() }) {
    coroutineContext.ensureActive()

    if (recentProcesses.contains(item.processInfo)) {
      continue
    }

    val itemNode = AttachDialogProcessNode(item, filters, columnsLayout)
    allItems.add(itemNode)
  }

  val allItemsSorted = allItems.sortedBy { itemNode -> itemNode.getProcessItem().getGroups().minBy { it.order }.order }
  if (itemNodes.any()) {
    itemNodes.add(AttachDialogGroupNode(XDebuggerBundle.message("xdebugger.attach.dialog.other.processes.message"), columnsLayout, allItemsSorted).apply {
      isFirstGroup = false
    })
  }

  itemNodes.addAll(allItemsSorted)

  return AttachToProcessItemsList(itemNodes, filters, columnsLayout, dialogState)
}
