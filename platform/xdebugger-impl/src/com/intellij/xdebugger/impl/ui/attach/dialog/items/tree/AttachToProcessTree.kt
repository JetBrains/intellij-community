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
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachItemsInfo
import com.intellij.xdebugger.impl.ui.attach.dialog.items.*
import kotlinx.coroutines.ensureActive
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import kotlin.coroutines.coroutineContext

private val logger = Logger.getInstance(AttachToProcessTree::class.java)


internal class AttachToProcessTree(
  rootNode: AttachTreeRootNode,
  dialogState: AttachDialogState) : TreeTable(
  FilteringTreeTableModel(AttachTreeModel(rootNode))), AttachToProcessItemsListBase {

  val filters = AttachToProcessElementsFilters(dialogState.selectedDebuggersFilter)

  init {

    rootNode.tree = this

    tree.isRootVisible = false

    for (index in 0 until columnCount) {
      val column = getColumn(index)
      column.minWidth = AttachDialogState.COLUMN_MINIMUM_WIDTH
      column.preferredWidth = dialogState.attachTreeColumnSettings.getColumnWidth(index)
      column.addPropertyChangeListener { if (it.propertyName == "width") dialogState.attachTreeColumnSettings.setColumnWidth(index, it.newValue as Int) }
    }
    setTreeCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
      if (value !is AttachTreeNode) throw IllegalStateException("Unexpected node type: ${value?.javaClass?.simpleName}")
      value.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }

    val filteringModel = getFilteringModel()
    filteringModel.setCondition {
      if (it !is AttachToProcessElement) return@setCondition true
      return@setCondition filters.matches(it)
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
    setRowHeight(AttachDialogState.DEFAULT_ROW_HEIGHT)
    installSelectionOnFocus()
    application.invokeLater({ focusFirst() }, ModalityState.any())
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    val attachTreeNode = model.getValueAt(row, 0) as? AttachTreeNode ?: return super.getCellRenderer(row, column)
    return attachTreeNode.getRenderer(column) ?: super.getCellRenderer(row, column)
  }

  override fun updateFilter(searchFilterValue: String) {
    filters.updatePattern(searchFilterValue)
    refilterSaveSelection()
  }

  override fun getFocusedComponent(): JComponent = this

  override fun addSelectionListener(disposable: Disposable, listenerAction: (AttachToProcessElement?) -> Unit) {
    val listListener = ListSelectionListener {
      listenerAction(getSelectedItem())
    }
    selectionModel.addListSelectionListener(listListener)
    Disposer.register(disposable) { selectionModel.removeListSelectionListener(listListener) }
  }

  override fun getSelectedItem(): AttachToProcessElement? = model.getValueAt<AttachTreeProcessNode>(selectedRow)
  override fun selectNextItem() {
    selectionModel.selectNext()
  }

  private fun refilterSaveSelection() {
    filters.clear()
    val previouslySelectedRow = selectedRow
    val selectedItem = if (previouslySelectedRow in 0 until rowCount) model.getValueAt<AttachTreeNode>(previouslySelectedRow) else null

    getFilteringModel().refilter()
    TreeUtil.expandAll(this.tree)
    updateUI()

    if (selectedItem == null) return
    for (index in 0 until rowCount) {
      val valueAt = getValueAt(index, 0)
      if (valueAt == selectedItem) {
        selectionModel.setSelectionInterval(index, index)
        return
      }
    }

    focusFirst()
  }

  private fun getColumn(index: Int): TableColumn = columnModel.getColumn(index)

  private fun getFilteringModel() = tableModel as FilteringTreeTableModel
}

internal class AttachTreeModel(private val rootNode: AttachTreeRootNode) : BaseTreeModel<AttachTreeNode>(), TreeTableModel {

  override fun getRoot(): Any = rootNode

  override fun getChildren(parent: Any?): List<AttachTreeNode> = (parent as? AttachTreeNode)?.getChildNodes() ?: emptyList()

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
    if (node is AttachTreeNode) return node.getValueAtColumn(column)
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
  dialogState: AttachDialogState): List<AttachTreeNode> {

  val elements = attachItemsInfo.processItems
  val pidToElement = elements.map { AttachTreeProcessNode(it, dialogState, attachItemsInfo.dataHolder) }.associateBy { it.item.processInfo.pid }
  val builtNodes = mutableMapOf<Int, AttachTreeProcessNode>()
  val topLevelNodes = mutableListOf<AttachTreeNode>()

  val recentItems = attachItemsInfo.recentItems
  if (recentItems.any()) {
    val recentItemNodes = recentItems.map { AttachTreeRecentProcessNode(it, dialogState, attachItemsInfo.dataHolder) }
    val recentNode = AttachTreeRecentNode(recentItemNodes)
    topLevelNodes.add(recentNode)
    topLevelNodes.add(AttachTreeSeparatorNode(recentItemNodes))
  }

  for (item in pidToElement) {
    coroutineContext.ensureActive()
    val pid = item.key
    if (builtNodes.containsKey(pid)) {
      continue
    }

    val visitedPids = mutableSetOf(pid)

    val treeElement = item.value

    builtNodes[pid] = treeElement

    var currentElement = treeElement
    while (currentElement.item.processInfo.parentPid > 0) {
      coroutineContext.ensureActive()
      val parentPid = currentElement.item.processInfo.parentPid

      if (visitedPids.contains(parentPid)) {
        logger.warn("Processes [${visitedPids.joinToString(", ")}] form a circle!")
        topLevelNodes.add(currentElement)
        break
      }

      val nextElement = builtNodes[parentPid]
      if (nextElement != null) {
        nextElement.addChild(currentElement)
        break
      }

      val parentElement = pidToElement[parentPid]
      if (parentElement == null) {
        topLevelNodes.add(currentElement)
        break
      }
      builtNodes[parentPid] = parentElement
      visitedPids.add(parentPid)

      parentElement.addChild(currentElement)
      currentElement = parentElement
    }

    if (currentElement.item.processInfo.parentPid <= 0) {
      topLevelNodes.add(currentElement)
    }
  }

  return topLevelNodes
}

internal suspend fun buildTree(
  attachItemsInfo: AttachItemsInfo,
  dialogState: AttachDialogState): AttachToProcessItemsListBase {
  val topLevelElements = prepareTreeElements(attachItemsInfo, dialogState)
  val rootNode = AttachTreeRootNode(topLevelElements)
  return AttachToProcessTree(rootNode, dialogState)
}

internal fun ListSelectionModel.selectNext() {
  val selectionIndex = minSelectionIndex
  setSelectionInterval(selectionIndex + 1, selectionIndex + 1)
}