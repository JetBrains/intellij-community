// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

private const val PREFERRED_HEIGHT = 150
private const val PREFERRED_WIDTH = 400

internal class ToolbarDecoratorPanel : UISandboxPanel {

  override val title: String = "ToolbarDecorator"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.toolbar.decorator.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.decorator.group.list")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.decorator.list.note"))
        }
        row {
          cell(createListDecorator())
        }
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.decorator.group.table")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.decorator.table.note"))
        }
        row {
          cell(createTableDecorator())
        }
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.decorator.group.tree")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.decorator.tree.note"))
        }
        row {
          cell(createTreeDecorator())
        }
      }
    }
  }

  private fun createListDecorator(): JComponent {
    val model = CollectionListModel(
      DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.java"),
      DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.kotlin"),
      DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.groovy"),
    )
    val list = JBList(model)
    return ToolbarDecorator.createDecorator(list)
      .setAddAction {
        model.add(DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.new", model.size + 1))
        list.selectedIndex = model.size - 1
      }
      .setEditAction {
        val index = list.selectedIndex
        if (index >= 0) {
          model.setElementAt(DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.edited", model.getElementAt(index)), index)
        }
      }
      .setPreferredSize(JBUI.size(PREFERRED_WIDTH, PREFERRED_HEIGHT))
      .createPanel()
  }

  private fun createTableDecorator(): JComponent {
    val tableModel = DefaultTableModel(
      arrayOf(
        arrayOf("JAVA_HOME", "C:\\Java\\jdk-21"),
        arrayOf("GRADLE_USER_HOME", "C:\\Gradle"),
      ),
      arrayOf(
        DevkitUiDslBundle.message("sandbox.toolbar.decorator.column.name"),
        DevkitUiDslBundle.message("sandbox.toolbar.decorator.column.value"),
      ),
    )
    val table = JBTable(tableModel)
    return ToolbarDecorator.createDecorator(table)
      .setAddAction {
        tableModel.addRow(arrayOf("NEW_VARIABLE", ""))
        val row = tableModel.rowCount - 1
        table.selectionModel.setSelectionInterval(row, row)
      }
      .setRemoveAction {
        for (row in table.selectedRows.reversed()) {
          tableModel.removeRow(row)
        }
      }
      .disableUpDownActions()
      .setPreferredSize(JBUI.size(PREFERRED_WIDTH, PREFERRED_HEIGHT))
      .createPanel()
  }

  private fun createTreeDecorator(): JComponent {
    val root = DefaultMutableTreeNode(DevkitUiDslBundle.message("sandbox.toolbar.decorator.node.project"))
    root.add(DefaultMutableTreeNode(DevkitUiDslBundle.message("sandbox.toolbar.decorator.node.sources")))
    root.add(DefaultMutableTreeNode(DevkitUiDslBundle.message("sandbox.toolbar.decorator.node.tests")))
    val treeModel = DefaultTreeModel(root)
    val tree = Tree(treeModel).apply {
      isRootVisible = false
      showsRootHandles = true
    }
    return ToolbarDecorator.createDecorator(tree)
      .setAddAction {
        val parent = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: root
        val child = DefaultMutableTreeNode(DevkitUiDslBundle.message("sandbox.toolbar.decorator.item.new", parent.childCount + 1))
        treeModel.insertNodeInto(child, parent, parent.childCount)
        tree.selectionPath = TreePath(child.path)
      }
      .setRemoveAction {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        if (selected?.parent != null) {
          treeModel.removeNodeFromParent(selected)
        }
      }
      .setPreferredSize(JBUI.size(PREFERRED_WIDTH, PREFERRED_HEIGHT))
      .createPanel()
  }
}
