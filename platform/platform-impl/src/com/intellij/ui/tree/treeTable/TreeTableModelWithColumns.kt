// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.treeTable

import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import javax.swing.JTree
import javax.swing.tree.TreeModel

class TreeTableModelWithColumns(val delegate: TreeModel,
                                val columns: Array<ColumnInfo<Any?, Any?>>)
  : TreeTableModel, TreeModel by delegate {

  override fun getColumnCount(): Int = columns.size

  override fun getColumnName(column: Int): String = columns[column].name

  override fun getColumnClass(column: Int): Class<*> = columns[column].columnClass

  override fun getValueAt(node: Any?, column: Int): Any? = columns[column].valueOf(node)

  override fun setValueAt(aValue: Any?, node: Any?, column: Int) = columns[column].setValue(node, aValue)

  override fun isCellEditable(node: Any?, column: Int): Boolean = columns[column].isCellEditable(node)

  override fun setTree(tree: JTree?) {
    // do nothing
  }
}