package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.speedSearch.FilteringTableModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachItemsInfo
import com.intellij.xdebugger.impl.ui.attach.dialog.items.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachTableCellRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.getValueAt
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupFirstColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupLastColumnRenderer
import kotlinx.coroutines.ensureActive
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import kotlin.coroutines.coroutineContext

internal class AttachToProcessItemsList(itemNodes: List<AttachToProcessElement>, val state: AttachDialogState) :
  JBTable(FilteringTableModel(AttachToProcessTableModel(itemNodes, state), Any::class.java),
          AttachToProcessListColumnModel()), AttachToProcessItemsListBase {

  private val speedSearch = SpeedSearch().apply {
    updatePattern("")
  }

  private val filters = AttachToProcessElementsFilters(speedSearch, state.selectedDebuggersFilter)

  init {
    for (index in 0 until columnCount) {
      val column = getColumn(index)
      column.minWidth = AttachDialogState.COLUMN_MINIMUM_WIDTH
      column.cellRenderer = AttachTableCellRenderer()
      column.preferredWidth = state.attachListColumnSettings.getColumnWidth(index)
      column.addPropertyChangeListener { if (it.propertyName == "width") state.attachListColumnSettings.setColumnWidth(index, it.newValue as Int) }
    }

    setShowGrid(false)
    intercellSpacing = Dimension(0, 0)

    getFilteringModel().setFilter {
      val node = transformToNode(it) ?: return@setFilter true
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
    updateRowsHeight()
    model.addTableModelListener(object : TableModelListener {
      override fun tableChanged(e: TableModelEvent?) {
        e ?: return
        updateRowsHeight(e.firstRow, e.lastRow)
      }
    })
  }

  private fun updateRowsHeight(from: Int = 0, to: Int = rowCount - 1) {
    setRowHeight(AttachDialogState.DEFAULT_ROW_HEIGHT)
    for (row in from until to + 1) {
      if (model.getValueAt<AttachToProcessElement>(row) is AttachToProcessListGroupBase) {
        setRowHeight(row, AttachDialogState.GROUP_ROW_HEIGHT)
      }
    }
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    val element = model.getValueAt<AttachToProcessElement>(row) ?: throw IllegalStateException("Should not be null")
    if (element is AttachToProcessListItem) return super.getCellRenderer(row, column)
    if (element !is AttachToProcessListGroupBase) throw IllegalStateException("Unexpected element type: ${element.javaClass.simpleName}")
    return when (column) {
      0 -> AttachGroupFirstColumnRenderer()
      1 -> AttachGroupColumnRenderer()
      2 -> AttachGroupLastColumnRenderer()
      else -> throw IllegalStateException("Unexpected column index: $column")
    }
  }

  override fun updateFilter(searchFilterValue: String) {
    speedSearch.updatePattern(searchFilterValue)
    refilterSaveSelection()
  }

  override fun getFocusedComponent(): JComponent = this

  override fun addSelectionListener(disposable: Disposable, listenerAction: (AttachToProcessElement?) -> Unit) {
    val listener = ListSelectionListener {
      listenerAction(getSelectedItem())
    }
    selectionModel.addListSelectionListener(listener)
    Disposer.register(disposable) { selectionModel.removeListSelectionListener(listener) }
  }

  override fun getSelectedItem(): AttachToProcessElement? {
    return model.getValueAt<AttachToProcessListItem>(selectedRow)
  }

  private fun transformToNode(obj: Any?): AttachToProcessElement? {
    return when(obj) {
      is AttachToProcessListGroupBase -> obj
      is ExecutableListCell -> obj.node
      else -> null
    }
  }

  private fun getFilteringModel(): FilteringTableModel<*> = model as FilteringTableModel<*>

  private fun refilterSaveSelection() {
    filters.clear()
    val previouslySelectedRow = selectedRow
    val selectedItem = if (previouslySelectedRow in 0 until rowCount) model.getValueAt<AttachToProcessElement>(previouslySelectedRow) else null
    getFilteringModel().refilter()
    updateRowsHeight()

    var isFirstGroup = true
    for (rowNumber in 0 until rowCount) {
      val valueAtRow = model.getValueAt<AttachToProcessElement>(rowNumber)
      if (valueAtRow is AttachToProcessListGroupBase) {
        valueAtRow.isFirstGroup = isFirstGroup
        isFirstGroup = false
      }
      if (selectedItem != null && valueAtRow != null && selectedItem == valueAtRow) {
        selectionModel.setSelectionInterval(rowNumber, rowNumber)
      }
    }

    focusFirst()
  }
}

class AttachToProcessListColumnModel : DefaultTableColumnModel() {
  init {
    addColumn(TableColumn(0).apply { identifier = 0; headerValue = XDebuggerBundle.message("xdebugger.attach.executable.column.name") })
    addColumn(TableColumn(1).apply { identifier = 1; headerValue = XDebuggerBundle.message("xdebugger.attach.pid.column.name") })
    addColumn(TableColumn(2).apply { identifier = 2; headerValue = XDebuggerBundle.message("xdebugger.attach.command.line.column.name") })
  }
}

internal class AttachToProcessTableModel(private val itemNodes: List<AttachToProcessElement>,
                                         private val state: AttachDialogState) : AbstractTableModel() {

  override fun getRowCount(): Int = itemNodes.size

  override fun getColumnCount(): Int = 3

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val itemNode = itemNodes[rowIndex]
    if (itemNode is AttachToProcessListGroupBase) return itemNode
    if (itemNode !is AttachToProcessListItem) throw IllegalStateException("Unexpected element type: ${itemNode.javaClass.simpleName}")
    return when (columnIndex) {
      0 -> ExecutableListCell(state.attachListColumnSettings, itemNode)
      1 -> PidListCell(itemNode.item.processInfo.pid, state.attachListColumnSettings)
      2 -> CommandLineListCell(itemNode, state.attachListColumnSettings)
      else -> throw IllegalStateException("Unexpected column number: $columnIndex")
    }
  }
}

internal suspend fun buildList(itemsInfo: AttachItemsInfo, dialogState: AttachDialogState): AttachToProcessItemsList {

  val allGroups = mutableSetOf<AttachToProcessListGroupBase>()

  val recentItems = itemsInfo.recentItems
  if (recentItems.any()) {
    val recentGroup = AttachToProcessListRecentGroup()
    allGroups.add(recentGroup)

    for (recentItem in recentItems) {
      recentGroup.add(AttachToProcessListItem(recentItem))
    }
  }

  val itemGroups = mutableMapOf<XAttachPresentationGroup<*>, AttachToProcessListGroup>()

  for (item in itemsInfo.processItems) {
    coroutineContext.ensureActive()

    val presentationGroup = item.getGroups().singleOrNull() ?: throw IllegalStateException("List view does not support items with several groups")
    itemGroups.putIfAbsent(presentationGroup, AttachToProcessListGroup(presentationGroup).apply { allGroups.add(this) })
    val group = itemGroups[presentationGroup] ?: throw IllegalStateException("Group should be available at this point")

    val itemNode = AttachToProcessListItem(item)
    group.add(itemNode)
  }

  val itemNodes = mutableListOf<AttachToProcessElement>()
  for (group in allGroups.sortedBy { it.getOrder() }) {
    itemNodes.add(group)
    itemNodes.addAll(group.getNodes())
  }

  return AttachToProcessItemsList(itemNodes, dialogState)
}