// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components

import com.intellij.ide.DataManager
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Comparing
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScreenUtil
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class DataContextDialog(
  project: Project?,
  val contextComponent: JComponent
) : DialogWrapper(project) {
  init {
    init()
  }

  override fun getDimensionServiceKey() = "UiInternal.DataContextDialog"

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction)
  }

  override fun createCenterPanel(): JComponent? {
    val table = JBTable()
    table.setDefaultRenderer(Object::class.java, MyTableCellRenderer())

    table.model = buildTreeModel(false)

    val panel = panel {
      row {
        @Suppress("DialogTitleCapitalization")
        checkBox("Show GetDataRule") // NON-NLS
          .actionListener { event, component -> table.model = buildTreeModel(component.isSelected) }
      }
      row {
        scrollCell(table)
          .align(Align.FILL)
          .resizableColumn()
      }.resizableRow()
    }

    val screenBounds = ScreenUtil.getMainScreenBounds()
    val width = (screenBounds.width * 0.8).toInt()
    val height = (screenBounds.height * 0.8).toInt()
    return panel.withPreferredSize(width, height)
  }

  private fun buildTreeModel(showDataRules: Boolean): DefaultTableModel {
    val model = DefaultTableModel()
    model.addColumn("Key")
    model.addColumn("Value")
    model.addColumn("Value type")

    val values = mutableMapOf<String, Any>()

    var component: Component? = contextComponent
    while (component != null) {
      val result = collectDataFrom(component, showDataRules)
        .filter { data -> !Comparing.equal(values[data.key], data.value) } // filter out identical overrides

      if (result.isNotEmpty()) {
        if (model.rowCount > 0) {
          model.appendEmptyRow()
        }
        model.appendHeader(component)

        for (data in result) {
          model.appendRow(data, values[data.key] != null)
          values[data.key] = data.value
        }
      }
      component = component.parent
    }
    return model
  }

  private fun DefaultTableModel.appendHeader(component: Component) {
    addRow(arrayOf(Header("$component"), null, getClassName(component)))
  }

  private fun DefaultTableModel.appendRow(data: ContextData, overridden: Boolean) {
    addRow(arrayOf(getKeyPresentation(data.key, overridden),
                   getValuePresentation(data.value),
                   getClassName(data.value)))
  }

  private fun DefaultTableModel.appendEmptyRow() {
    addRow(arrayOf("", "", ""))
  }

  private fun collectDataFrom(component: Component, showDataRules: Boolean): List<ContextData> {
    val dataManager = DataManager.getInstance()
    val provider = DataManagerImpl.getDataProviderEx(component) ?: return emptyList()
    val result = mutableListOf<ContextData>()
    for (key in DataKey.allKeys()) {
      val data =
        when {
          showDataRules -> dataManager.getCustomizedData(
            key.name, dataManager.getDataContext(component.parent), provider)
          else -> provider.getData(key.name)
        } ?: continue
      result += ContextData(key.name, data)
    }
    return result
  }

  private fun getKeyPresentation(key: String, overridden: Boolean) = when {
    overridden -> "*OVERRIDDEN* ${key}"
    else -> key
  }

  private fun getValuePresentation(value: Any) = when (value) {
    is Array<*> -> value.contentToString()
    else -> value.toString()
  }

  private fun getClassName(value: Any): String {
    val clazz: Class<*> = value.javaClass
    return when {
      clazz.isAnonymousClass -> "${clazz.superclass.simpleName}$..."
      else -> clazz.simpleName
    }
  }

  private class MyTableCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value != null) {
        append(value.toString())
      }

      val isHeader = table.model.getValueAt(row, 0) is Header
      if (isHeader) {
        background = JBUI.CurrentTheme.Table.Selection.background(false)
      }
    }
  }

  private class ContextData(val key: String, val value: Any)

  private class Header(val key: String) {
    override fun toString(): String = key
  }
}