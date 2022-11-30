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
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayoutService
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.applyColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import kotlinx.coroutines.ensureActive
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer
import kotlin.coroutines.coroutineContext

private val logger = Logger.getInstance(AttachToProcessItemsTree::class.java)


internal class AttachToProcessItemsTree(
  rootNode: AttachTreeNodeWrapper,
  columnsLayout: AttachDialogColumnsLayout,
  dialogState: AttachDialogState,
  private val filters: AttachToProcessElementsFilters) : TreeTable(
  FilteringTreeTableModel(AttachTreeModel(rootNode, columnsLayout))), AttachToProcessItemsListBase {

  init {

    tree.isRootVisible = false

    applyColumnsLayout(columnsLayout)
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

  private fun getFilteringModel() = tableModel as FilteringTreeTableModel
}

internal class AttachTreeModel(private val rootNode: AttachTreeNodeWrapper, private val columnsLayout: AttachDialogColumnsLayout) : BaseTreeModel<AttachTreeNodeWrapper>(), TreeTableModel {

  override fun getRoot(): Any = rootNode

  override fun getChildren(parent: Any?): List<AttachTreeNodeWrapper> = (parent as? AttachTreeNodeWrapper)?.getChildNodes()
                                                                        ?: emptyList()

  override fun getColumnCount(): Int = columnsLayout.getColumnsCount()

  override fun getColumnName(column: Int): String = columnsLayout.getColumnName(columnsLayout.getColumnKey(column))

  override fun getColumnClass(column: Int): Class<*> = if (column == 0) TreeTableModel::class.java else columnsLayout.getColumnClass(column)

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
  columnsLayout: AttachDialogColumnsLayout,
  filters: AttachToProcessElementsFilters): List<AttachTreeNodeWrapper> {

  val elements = attachItemsInfo.processItems
  val pidToElement = elements.map {
    AttachDialogProcessNode(it, filters, columnsLayout)
  }.associateBy { it.item.processInfo.pid }
  val builtNodes = mutableMapOf<Int, AttachTreeNodeWrapper>()

  val topLevelNodes = mutableListOf<AttachTreeNodeWrapper>()

  val recentItems = attachItemsInfo.recentItems
  if (recentItems.any()) {
    val recentItemNodes = recentItems.map { AttachDialogProcessNode(it, filters, columnsLayout) }
    val recentNode = AttachDialogGroupNode(
      XDebuggerBundle.message("xdebugger.attach.dialog.recently.attached.message"),
      columnsLayout,
      recentItemNodes)
    topLevelNodes.add(AttachTreeNodeWrapper(recentNode, filters, columnsLayout))
    topLevelNodes.addAll(recentItemNodes.map { AttachTreeNodeWrapper(it, filters, columnsLayout) })
    topLevelNodes.add(
      AttachTreeNodeWrapper(
        AttachDialogGroupNode(
          null,
          //XDebuggerBundle.message("xdebugger.attach.dialog.all.processes.message"),
          columnsLayout,
          listOf(recentNode)), filters, columnsLayout))
  }

  for (item in pidToElement) {
    coroutineContext.ensureActive()
    val pid = item.key
    if (builtNodes.containsKey(pid)) {
      continue
    }

    val visitedPids = mutableSetOf(pid)

    val attachTreeProcessNode = item.value
    val treeElement = AttachTreeNodeWrapper(attachTreeProcessNode, filters, columnsLayout)

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
      val parentTreeElement = AttachTreeNodeWrapper(parentElement, filters, columnsLayout)
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
  val columnsLayout = application.getService(AttachDialogColumnsLayoutService::class.java).getColumnsLayout()
  val topLevelElements = prepareTreeElements(attachItemsInfo, columnsLayout, filters)
  val rootNode = AttachTreeNodeWrapper(AttachTreeRootNode(), filters, columnsLayout, -1)
  topLevelElements.forEach { rootNode.addChild(it) }
  return AttachToProcessItemsTree(rootNode, columnsLayout, dialogState, filters)
}

internal fun ListSelectionModel.selectNext() {
  val selectionIndex = minSelectionIndex
  setSelectionInterval(selectionIndex + 1, selectionIndex + 1)
}