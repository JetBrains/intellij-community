package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.ui.treeStructure.treetable.TreeTableModel
import javax.swing.JTree
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath

class FilteringTreeTableModel(private val initialModel: TreeTableModel) : TreeTableModel {

  private var acceptedNodeIndex: Map<Any, List<Int>>
  private var condition: (Any) -> Boolean = { true }

  init {
    acceptedNodeIndex = buildAcceptanceCache()
  }

  override fun getRoot(): Any = initialModel.root

  override fun getChild(parent: Any?, index: Int): Any? {
    val childrenMask = acceptedNodeIndex[parent] ?: return null
    if (index < 0 || index >= childrenMask.size) return null
    val initialIndex = childrenMask[index]
    return initialModel.getChild(parent, initialIndex)
  }

  override fun getChildCount(parent: Any?): Int = acceptedNodeIndex[parent]?.size ?: 0

  override fun isLeaf(node: Any?): Boolean = acceptedNodeIndex[node]?.isEmpty() ?: true

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
    initialModel.valueForPathChanged(path, newValue)
  }

  override fun getIndexOfChild(parent: Any?, child: Any?): Int {
    val childrenMask = acceptedNodeIndex[parent] ?: return -1
    val initialIndex = initialModel.getIndexOfChild(parent, child)
    if (initialIndex == -1) return -1
    return childrenMask.indexOf(initialIndex)
  }

  override fun addTreeModelListener(l: TreeModelListener?) {
    initialModel.addTreeModelListener(l)
  }

  override fun removeTreeModelListener(l: TreeModelListener?) {
    initialModel.removeTreeModelListener(l)
  }

  override fun getColumnCount(): Int = initialModel.columnCount

  override fun getColumnName(column: Int): String = initialModel.getColumnName(column)

  override fun getColumnClass(column: Int): Class<*> = initialModel.getColumnClass(column)

  override fun getValueAt(node: Any?, column: Int): Any = initialModel.getValueAt(node, column)

  override fun isCellEditable(node: Any?, column: Int): Boolean {
    return initialModel.isCellEditable(node, column)
  }

  override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
    initialModel.setValueAt(aValue, node, column)
  }

  override fun setTree(tree: JTree?) {
    initialModel.setTree(tree)
  }

  fun setCondition(condition: (Any) -> Boolean) {
    this.condition = condition
  }

  fun refilter() {
    acceptedNodeIndex = buildAcceptanceCache()
  }

  private fun buildAcceptanceCache(): Map<Any, List<Int>> {
    val acceptedNodesIndicesToChildren = mutableMapOf<Any, List<Int>>()

    processNode(root, acceptedNodesIndicesToChildren)

    return acceptedNodesIndicesToChildren
  }

  private fun processNode(node: Any, acceptedNodesIndicesToChildren: MutableMap<Any, List<Int>>): Boolean {
    var isAccepted = false
    val acceptedChildrenIndices = mutableListOf<Int>()

    for (i in 0 until initialModel.getChildCount(node)) {
      val child = initialModel.getChild(node, i)
      val isChildAccepted = processNode(child, acceptedNodesIndicesToChildren)
      if (isChildAccepted) {
        acceptedChildrenIndices.add(i)
      }
      isAccepted = isChildAccepted or isAccepted
    }

    isAccepted = isAccepted or matches(node)
    if (isAccepted) {
      acceptedNodesIndicesToChildren[node] = acceptedChildrenIndices
    }

    return isAccepted
  }

  private fun matches(item: Any): Boolean = condition(item)
}