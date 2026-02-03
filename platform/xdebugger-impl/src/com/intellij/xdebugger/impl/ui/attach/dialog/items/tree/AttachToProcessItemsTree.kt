// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachItemsInfo
import com.intellij.xdebugger.impl.ui.attach.dialog.items.*
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.AttachTableCell
import com.intellij.xdebugger.impl.ui.attach.dialog.items.cells.AttachTableCellRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
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

  private val emptyText = AttachDialogEmptyText(this, filters)

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

  override fun getEmptyText(): StatusText = emptyText

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

  internal fun treeStructureChanged() {
    super.treeStructureChanged(null, null, null)
  }
}

internal suspend fun buildTree(
  attachItemsInfo: AttachItemsInfo,
  dialogState: AttachDialogState,
  columnsLayout: AttachDialogColumnsLayout
): AttachToProcessItemsListBase {
  val filters = AttachToProcessElementsFilters(dialogState.selectedDebuggersFilter)
  val rootNode = AttachTreeNodeWrapper(AttachTreeRootNode(), filters, columnsLayout, -1)

  val recentItemNodes = prepareRecentItemNodes(attachItemsInfo, filters, columnsLayout)
  for (node in recentItemNodes) {
    rootNode.addChild(node)
  }

  val processItemNodes = prepareProcessItemNodes(attachItemsInfo, columnsLayout, filters)
  for (node in processItemNodes) {
    rootNode.addChild(node)
  }

  return AttachToProcessItemsTree(rootNode, columnsLayout, dialogState, filters)
}

private fun prepareRecentItemNodes(
  attachItemsInfo: AttachItemsInfo,
  filters: AttachToProcessElementsFilters,
  columnsLayout: AttachDialogColumnsLayout
): List<AttachTreeNodeWrapper> {
  val topLevelNodes = mutableListOf<AttachTreeNodeWrapper>()

  val recentItems = attachItemsInfo.recentItems
  if (recentItems.isNotEmpty()) {
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

  return topLevelNodes
}

private suspend fun prepareProcessItemNodes(
  attachItemsInfo: AttachItemsInfo,
  columnsLayout: AttachDialogColumnsLayout,
  filters: AttachToProcessElementsFilters
): List<AttachTreeNodeWrapper> {
  val topLevelNodes = mutableListOf<AttachTreeNodeWrapper>()

  val processItems = attachItemsInfo.processItems.associateBy {
    coroutineContext.ensureActive()
    it.processInfo.pid
  }
  val builtElements = mutableMapOf<Int, AttachTreeNodeWrapper>()

  for (entry in processItems) {
    coroutineContext.ensureActive()
    if (entry.key in builtElements) {
      continue
    }

    val visitedPids = mutableSetOf<Int>()

    var lastTreeElement: AttachTreeNodeWrapper? = null
    var currentItem: AttachDialogProcessItem? = entry.value

    while (currentItem != null) {
      val node = AttachDialogProcessNode(currentItem, filters, columnsLayout)
      val treeElement = AttachTreeNodeWrapper(node, filters, columnsLayout)
      if (lastTreeElement != null) {
        treeElement.addChild(lastTreeElement)
      }
      lastTreeElement = treeElement

      val processInfo = currentItem.processInfo
      val pid = processInfo.pid

      builtElements[pid] = treeElement
      visitedPids += pid

      val parentPid = processInfo.parentPid
      if (parentPid <= 0) {
        topLevelNodes.add(treeElement)
        break
      }

      if (parentPid in visitedPids) {
        logger.warn("Processes [${visitedPids.joinToString()}] form a cycle!")
        topLevelNodes.add(treeElement)
        break
      }

      val builtTreeElement = builtElements[parentPid]
      if (builtTreeElement != null) {
        builtTreeElement.addChild(treeElement)
        break
      }

      coroutineContext.ensureActive()
      currentItem = processItems[parentPid]
      if (currentItem == null) {
        logger.debug("Process PID $pid has a non-existent parent PID $parentPid")
        topLevelNodes.add(treeElement)
        break
      }
    }
  }

  return topLevelNodes
}

internal fun ListSelectionModel.selectNext() {
  val selectionIndex = minSelectionIndex
  setSelectionInterval(selectionIndex + 1, selectionIndex + 1)
}