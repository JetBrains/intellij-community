package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.application
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogColumnsState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachItemsInfo
import com.intellij.xdebugger.impl.ui.attach.dialog.items.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.CommandLineCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.DebuggersCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.PidCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import kotlinx.coroutines.ensureActive
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import kotlin.coroutines.coroutineContext

private val logger = Logger.getInstance(AttachToProcessItemsTree::class.java)


internal class AttachToProcessItemsTree(
  rootNode: AttachTreeNodeWrapper,
  dialogState: AttachDialogState,
  private val filters: AttachToProcessElementsFilters) : TreeTable(
  FilteringTreeTableModel(AttachTreeModel(rootNode))), AttachToProcessItemsListBase {

  init {

    tree.isRootVisible = false

    for (index in 0 until columnCount) {
      val column = getColumn(index)
      column.minWidth = AttachDialogState.COLUMN_MINIMUM_WIDTH
      column.preferredWidth = dialogState.attachTreeColumnSettings.getColumnWidth(index)
      column.addPropertyChangeListener {
        if (it.propertyName == "width") dialogState.attachTreeColumnSettings.setColumnWidth(index, it.newValue as Int)
      }
    }
    setTreeCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
      if (value !is AttachTreeNodeWrapper) throw IllegalStateException("Unexpected node type: ${value?.javaClass?.simpleName}")
      value.getTreeCellRendererComponent(tree, selected, expanded, leaf, row, hasFocus)
    }

    val filteringModel = getFilteringModel()
    filteringModel.setCondition {
      val node = tryCastValue<AttachDialogElementNode>(it) ?: return@setCondition true
      return@setCondition filters.matches(node)
    }

    dialogState.selectedDebuggersFilter.afterChange {
      refilterSaveSelection()
    }

    setDefaultRenderer(AttachTableCell::class.java, AttachTableCellRenderer())
    setEmptyState(XDebuggerBundle.message("xdebugger.attach.popup.emptyText"))

    getTableHeader().reorderingAllowed = false
    setSelectionModel(AttachToProcessTableSelectionModel(this))

    TreeUtil.expandAll(this.tree)
    resetDefaultFocusTraversalKeys()
    installSelectionOnFocus()

    setRowHeight(AttachDialogState.DEFAULT_ROW_HEIGHT)
    //installRowsHeightUpdater()
    //
    //tree.addTreeExpansionListener(object : TreeExpansionListener {
    //  override fun treeExpanded(event: TreeExpansionEvent?) {
    //    updateRowsHeight()
    //  }
    //
    //  override fun treeCollapsed(event: TreeExpansionEvent?) {
    //    updateRowsHeight()
    //  }
    //})

    application.invokeLater({ focusFirst() }, ModalityState.any())
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    val attachTreeNode = model.getValueAt<AttachDialogElementNode>(row) ?: return super.getCellRenderer(row, column)
    return attachTreeNode.getRenderer(column) ?: super.getCellRenderer(row, column)
  }

  override fun updateFilter(searchFilterValue: String) {
    filters.updatePattern(searchFilterValue)
    refilterSaveSelection()
  }

  override fun getFocusedComponent(): JComponent = this

  override fun addSelectionListener(disposable: Disposable, listenerAction: (AttachDialogElementNode?) -> Unit) {
    val listListener = ListSelectionListener {
      listenerAction(getSelectedItem())
    }
    selectionModel.addListSelectionListener(listListener)
    Disposer.register(disposable) { selectionModel.removeListSelectionListener(listListener) }
  }

  override fun getSelectedItem(): AttachDialogElementNode? = model.getValueAt<AttachDialogElementNode>(selectedRow)
  override fun selectNextItem() {
    selectionModel.selectNext()
  }

  private fun refilterSaveSelection() {
    refilterSaveSelection(filters) {
      getFilteringModel().refilter()
      TreeUtil.expandAll(this.tree)
      updateUI()
    }
  }

  private fun getColumn(index: Int): TableColumn = columnModel.getColumn(index)

  private fun getFilteringModel() = tableModel as FilteringTreeTableModel
}

internal class AttachTreeModel(private val rootNode: AttachTreeNodeWrapper) : BaseTreeModel<AttachTreeNodeWrapper>(), TreeTableModel {

  override fun getRoot(): Any = rootNode

  override fun getChildren(parent: Any?): List<AttachTreeNodeWrapper> = (parent as? AttachTreeNodeWrapper)?.getChildNodes()
                                                                        ?: emptyList()

  override fun getColumnCount(): Int = 4

  override fun getColumnName(column: Int): String {
    return when (column) {
      0 -> XDebuggerBundle.message("xdebugger.attach.executable.column.name")
      1 -> XDebuggerBundle.message("xdebugger.attach.pid.column.name")
      2 -> XDebuggerBundle.message("xdebugger.attach.debuggers.column.name")
      3 -> XDebuggerBundle.message("xdebugger.attach.command.line.column.name")
      else -> {
        logger.error("Unexpected column index: $column")
        ""
      }
    }
  }

  override fun getColumnClass(column: Int): Class<*> =
    when (column) {
      0 -> TreeTableModel::class.java
      1 -> PidCell::class.java
      2 -> DebuggersCell::class.java
      3 -> CommandLineCell::class.java
      else -> {
        logger.error("Unexpected column index: $column")
        Any::class.java
      }
    }

  override fun getValueAt(node: Any?, column: Int): Any {
    if (node is AttachTreeNodeWrapper) return node.getValueAtColumn(column)
    return node ?: Any()
  }

  override fun isCellEditable(node: Any?, column: Int): Boolean = false

  override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
  }

  override fun setTree(tree: JTree?) {
  }

}

private suspend fun prepareTreeElements(
  attachItemsInfo: AttachItemsInfo,
  dialogState: AttachDialogState,
  filters: AttachToProcessElementsFilters): List<AttachTreeNodeWrapper> {

  val elements = attachItemsInfo.processItems
  val pidToElement = elements.map {
    AttachDialogProcessNode(it, filters, dialogState)
  }.associateBy { it.item.processInfo.pid }
  val builtNodes = mutableMapOf<Int, AttachTreeNodeWrapper>()

  val topLevelNodes = mutableListOf<AttachTreeNodeWrapper>()

  val recentItems = attachItemsInfo.recentItems
  if (recentItems.any()) {
    val recentItemNodes = recentItems.map { AttachDialogProcessNode(it, filters, dialogState) }
    val recentNode = AttachDialogGroupNode(
      XDebuggerBundle.message("xdebugger.attach.dialog.recently.attached.message"),
      AttachDialogColumnsState(),
      recentItemNodes)
    topLevelNodes.add(AttachTreeNodeWrapper(recentNode, filters, dialogState))
    topLevelNodes.addAll(recentItemNodes.map { AttachTreeNodeWrapper(it, filters, dialogState) })
    topLevelNodes.add(
      AttachTreeNodeWrapper(
        AttachDialogGroupNode(
          null,
          //XDebuggerBundle.message("xdebugger.attach.dialog.all.processes.message"),
          AttachDialogColumnsState(),
          listOf(recentNode)), filters, dialogState))
  }

  for (item in pidToElement) {
    coroutineContext.ensureActive()
    val pid = item.key
    if (builtNodes.containsKey(pid)) {
      continue
    }

    val visitedPids = mutableSetOf(pid)

    val attachTreeProcessNode = item.value
    val treeElement = AttachTreeNodeWrapper(attachTreeProcessNode, filters, dialogState)

    builtNodes[pid] = treeElement

    var currentElement = attachTreeProcessNode
    var currentTreeElement = treeElement
    while (attachTreeProcessNode.item.processInfo.parentPid > 0) {
      coroutineContext.ensureActive()
      val parentPid = attachTreeProcessNode.item.processInfo.parentPid

      if (visitedPids.contains(parentPid)) {
        logger.warn("Processes [${visitedPids.joinToString(", ")}] form a circle!")
        topLevelNodes.add(treeElement)
        break
      }

      val nextElement = builtNodes[parentPid]
      if (nextElement != null) {
        nextElement.addChild(currentTreeElement)
        break
      }

      val parentElement = pidToElement[parentPid]
      if (parentElement == null) {
        topLevelNodes.add(currentTreeElement)
        break
      }
      val parentTreeElement = AttachTreeNodeWrapper(parentElement, filters, dialogState)
      builtNodes[parentPid] = parentTreeElement
      visitedPids.add(parentPid)

      parentTreeElement.addChild(currentTreeElement)
      currentElement = parentElement
      currentTreeElement = parentTreeElement
    }

    if (currentElement.item.processInfo.parentPid <= 0) {
      topLevelNodes.add(currentTreeElement)
    }
  }

  return topLevelNodes
}

internal suspend fun buildTree(
  attachItemsInfo: AttachItemsInfo,
  dialogState: AttachDialogState): AttachToProcessItemsListBase {
  val filters = AttachToProcessElementsFilters(dialogState.selectedDebuggersFilter)
  val topLevelElements = prepareTreeElements(attachItemsInfo, dialogState, filters)
  val rootNode = AttachTreeNodeWrapper(AttachTreeRootNode(), filters, dialogState, -1)
  topLevelElements.forEach { rootNode.addChild(it) }
  return AttachToProcessItemsTree(rootNode, dialogState, filters)
}

internal fun ListSelectionModel.selectNext() {
  val selectionIndex = minSelectionIndex
  setSelectionInterval(selectionIndex + 1, selectionIndex + 1)
}