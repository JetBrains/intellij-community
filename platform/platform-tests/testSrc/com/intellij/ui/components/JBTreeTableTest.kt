// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.concurrency.Invoker
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.SortableColumnModel
import com.intellij.util.ui.tree.AbstractTreeModel
import org.junit.Test
import javax.swing.JTree
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.table.TableModel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.assertEquals

class JBTreeTableTest : HeavyPlatformTestCase() {

  @Test
  fun testSorting() {
    val model = ElementModel(project)
    val treeTable = JBTreeTable(model)
    treeTable.tree.isRootVisible = true
    val rowSorter = JBTreeTableRowSorter(model, treeTable)
    treeTable.setRowSorter(rowSorter)

    rowSorter.toggleSortOrder(0)
    assertTree(treeTable.tree,
               """
               |-a:1
               | -b:42
               |  -c:12
               |   e:0
               |   f:4
               |  d:3
               |
               """.trimMargin())

    rowSorter.toggleSortOrder(0)
    assertTree(treeTable.tree,
               """
               |-a:1
               | -b:42
               |  d:3
               |  -c:12
               |   f:4
               |   e:0
               |
               """.trimMargin())

    rowSorter.toggleSortOrder(1)
    assertTree(treeTable.tree,
               """
               |-a:1
               | -b:42
               |  d:3
               |  -c:12
               |   e:0
               |   f:4
               |
               """.trimMargin())

    rowSorter.toggleSortOrder(1)
    assertTree(treeTable.tree,
               """
               |-a:1
               | -b:42
               |  -c:12
               |   f:4
               |   e:0
               |  d:3
               |
               """.trimMargin())
  }
}

private fun assertTree(tree: JTree, expected: String) {
  val actual = TreeTestUtil(tree).expandAll().setConverter { printNode(it) }.toString()
  assertEquals(expected, actual)
}

private fun printNode(node: Any): String {
  if (node !is ElementNodeDescriptor) return "ERROR"
  return node.element.run { "$name:$value" }
}

private class ElementModel(project: Project) : AbstractTreeModel(), TreeTableModel, SortableColumnModel, TreeModelListener {
  val structure = ElementTreeStructure(project)
  val model = StructureTreeModel(structure, null, Invoker.forEventDispatchThread(project), project)
  val columns = arrayOf(ElementColumnInfo("Name", 0), ElementColumnInfo("Value", 1))
  private var tree: JTree? = null

  init {
    model.addTreeModelListener(this)
  }

  fun setComparator(comparator: Comparator<in NodeDescriptor<*>>?) = model.setComparator(comparator)

  override fun getRoot(): Any? = model.root
  override fun getChild(parent: Any?, index: Int): Any? = model.getChild(parent, index)
  override fun getChildCount(parent: Any?) = model.getChildCount(parent)
  override fun isLeaf(node: Any?) = model.isLeaf(node)
  override fun getIndexOfChild(parent: Any?, child: Any?) = model.getIndexOfChild(parent, child)
  override fun getColumnCount() = columns.size
  override fun getColumnName(column: Int): String = columns[column].name
  override fun getColumnClass(column: Int): Class<*> = columns[column].columnClass
  override fun getValueAt(node: Any?, column: Int) = columns[column].valueOf(getElement(node))
  override fun isCellEditable(node: Any?, column: Int) = false
  override fun setValueAt(aValue: Any?, node: Any?, column: Int) {}
  override fun setTree(tree: JTree?) {
    this.tree = tree
  }

  private fun getElement(node: Any?): Element? {
    if (node is Element) return node
    if (node is DefaultMutableTreeNode) {
      val userObject = node.userObject
      if (userObject is Element) return userObject
    }
    return null
  }

  override fun getColumnInfos() = columns
  override fun setSortable(aBoolean: Boolean) {}
  override fun isSortable() = true
  override fun getRowValue(row: Int) = tree?.getPathForRow(row)?.lastPathComponent
  override fun getDefaultSortKey() = null

  override fun treeNodesChanged(e: TreeModelEvent?) = treeNodesChanged(e!!.treePath, e.childIndices, e.children)
  override fun treeNodesInserted(e: TreeModelEvent?) = treeNodesInserted(e!!.treePath, e.childIndices, e.children)
  override fun treeNodesRemoved(e: TreeModelEvent?) = treeNodesRemoved(e!!.treePath, e.childIndices, e.children)
  override fun treeStructureChanged(e: TreeModelEvent?) = treeStructureChanged(e!!.treePath, e.childIndices, e.children)
}

private class ElementColumnInfo(name: String, val column: Int) : ColumnInfo<Element, String>(name) {
  override fun valueOf(item: Element?) = item?.let {
    if (column == 0) it.name else it.value.toString()
  }

  override fun getComparator() = Comparator<Element> { o1, o2 ->
    if (column == 0) StringUtil.compare(o1?.name, o2?.name, false)
    else o1!!.value - o2!!.value
  }
}

private data class Element(val name: String, val value: Int)

private class ElementTreeStructure(private val project: Project) : AbstractTreeStructure() {
  val a = Element("a", 1)
  val b = Element("b", 42)
  val c = Element("c", 12)
  val d = Element("d", 3)
  val e = Element("e", 0)
  val f = Element("f", 4)

  private val root = a
  private val parent = hashMapOf<Element, Element?>()
  private val children = hashMapOf(
    a to listOf(b),
    b to listOf(c, d),
    c to listOf(e, f),
    d to listOf(),
    e to listOf(),
    f to listOf(),
  ).onEach { (p, c) ->
    c.forEach { parent[it] = p }
  }

  override fun getRootElement(): Any = root
  override fun getChildElements(element: Any): Array<Any> = (element as? Element).let { children[it]?.toTypedArray() } ?: emptyArray()
  override fun getParentElement(element: Any): Any? = (element as? Element).let { parent[it] }
  override fun commit() {}
  override fun hasSomethingToCommit() = false
  override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
    if (element is NodeDescriptor<*>) return element
    check(element is Element)
    return ElementNodeDescriptor(project, parentDescriptor, element)
  }
}

private class ElementNodeDescriptor(
  project: Project,
  parentDescriptor: NodeDescriptor<*>?,
  private val element: Element) : NodeDescriptor<Any>(project, parentDescriptor) {

  override fun update() = false
  override fun getElement() = element
}

private class JBTreeTableRowSorter(private val model: ElementModel, private val treeTable: JBTreeTable) : RowSorter<TableModel>() {
  private var mySortKey: SortKey? = null
  override fun getModel() = treeTable.table.model

  override fun toggleSortOrder(column: Int) {
    val sortOrder = if (mySortKey != null && mySortKey!!.column == column && mySortKey!!.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
    setSortKeys(listOf(SortKey(column, sortOrder)))
  }

  override fun convertRowIndexToModel(index: Int) = index
  override fun convertRowIndexToView(index: Int) = index

  override fun getSortKeys() = mySortKey?.let { listOf(it) } ?: emptyList()

  override fun setSortKeys(keys: List<SortKey>?) {
    if (keys == null || keys.isEmpty()) return
    val key = keys[0]
    if (key.sortOrder == SortOrder.UNSORTED) return
    mySortKey = key
    val columnInfo: ColumnInfo<*, *> = model.columnInfos[key.column]
    val comparator = columnInfo.comparator as? Comparator<Element> ?: return
    fireSortOrderChanged()
    val revert = if (key.sortOrder == SortOrder.DESCENDING) -1 else 1
    model.setComparator(Comparator<NodeDescriptor<Element>> { o1, o2 ->
      revert * comparator.compare(o1.element, o2.element)
    } as Comparator<in NodeDescriptor<*>>)
  }

  override fun getViewRowCount() = treeTable.tree.rowCount
  override fun getModelRowCount() = treeTable.tree.rowCount
  override fun modelStructureChanged() {}
  override fun allRowsChanged() {}
  override fun rowsInserted(firstRow: Int, endRow: Int) {}
  override fun rowsDeleted(firstRow: Int, endRow: Int) {}
  override fun rowsUpdated(firstRow: Int, endRow: Int) {}
  override fun rowsUpdated(firstRow: Int, endRow: Int, column: Int) {}
}
