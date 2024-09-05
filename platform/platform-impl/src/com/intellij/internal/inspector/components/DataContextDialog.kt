// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScreenUtil
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ReflectionUtil
import com.intellij.util.SlowOperations
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.lang.ref.Reference
import java.lang.reflect.Modifier
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

  override fun getDimensionServiceKey(): String = "UiInternal.DataContextDialog"

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction)
  }

  override fun createCenterPanel(): JComponent? {
    val table = JBTable()
    table.setDefaultRenderer(Object::class.java, MyTableCellRenderer())

    table.model = buildTreeModel(false)
    TableSpeedSearch.installOn(table)

    val panel = panel {
      row {
        checkBox("Show BGT Data")
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
    val model = object : DefaultTableModel()  {
      override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    model.addColumn("Key")
    model.addColumn("Value")
    model.addColumn("Value type")

    var component: Component? = contextComponent
    while (component != null) {
      val c = component
      val token = if (showDataRules) SlowOperations.knownIssue("move collection to BGT")
      else AccessToken.EMPTY_ACCESS_TOKEN
      val result = token.use {
        collectDataFrom(c, showDataRules)
      }
      if (result.isNotEmpty()) {
        if (model.rowCount > 0) {
          model.appendEmptyRow()
        }
        model.appendHeader(c)
        for (data in result) {
          model.appendRow(data)
        }
      }
      component = component.parent
    }
    return model
  }

  private fun DefaultTableModel.appendHeader(component: Component) {
    addRow(arrayOf(Header("$component"), null, getClassName(component)))
  }

  private fun DefaultTableModel.appendRow(data: ContextData) {
    addRow(arrayOf(getKeyPresentation(data.key, data.overridden), data.valueStr, data.valueClass))
  }

  private fun DefaultTableModel.appendEmptyRow() {
    addRow(arrayOf("", "", ""))
  }

  private fun collectDataFrom(component: Component, showDataRules: Boolean): List<ContextData> {
    val dataManager = DataManager.getInstance()
    val context = dataManager.getDataContext(component).let {
      if (showDataRules) it else Utils.getUiOnlyDataContext(it)
    }
    val parentContext = dataManager.getDataContext(component.parent).let {
      if (showDataRules) it else Utils.getUiOnlyDataContext(it)
    }
    val result = mutableListOf<ContextData>()
    for (key in DataKey.allKeys()) {
      val specialKey = isSpecialContextComponentOnlyKey(key)
      if (specialKey && component !== contextComponent) continue
      val data = context.getData(key)
      val parentData = if (specialKey) null else parentContext.getData(key)
      if (equalData(data, parentData, 0, null)) continue
      result += ContextData(
        key.name,
        getValuePresentation(data ?: CustomizedDataContext.EXPLICIT_NULL),
        getClassName(data ?: CustomizedDataContext.EXPLICIT_NULL),
        parentData != null && key != PlatformCoreDataKeys.BGT_DATA_PROVIDER)
    }
    result.sortWith(Comparator.comparing { StringUtil.toUpperCase(it.key) })
    return result
  }

  private fun equalData(o1: Any?, o2: Any?, level: Int, visited: MutableSet<Any>?): Boolean {
    class P(val o1: Any, val o2: Any) {
      override fun equals(other: Any?): Boolean = other is P && o1 === other.o1 && o2 === other.o2
      override fun hashCode(): Int = o1.hashCode() + o2.hashCode() * 31
    }
    fun set() = (visited ?: HashSet<Any>()).apply { add(P(o1!!, o2!!)) }
    if (o1 === o2) return true
    if (o1 == null || o2 == null) return false
    val c1 = o1.javaClass
    val c2 = o2.javaClass
    return when {
      level > 10 -> visited?.contains(P(o1, o2)) == true
      Comparing.equal(o1, o2) -> true
      o1 is Array<*> && o2 is Array<*> -> equalData(o1.toList(), o2.toList(), level + 1, set())
      o1 is Reference<*> && o2 is Reference<*> -> Comparing.equal(o1.get(), o2.get())
      o1 is Collection<*> && o2 is Collection<*> -> {
        if (o1.size != o2.size) return false
        val it1 = o1.iterator()
        val it2 = o2.iterator()
        while (it1.hasNext()) {
          if (!equalData(it1.next(), it2.next(), level + 1, set())) return false
        }
        true
      }
      c1 != c2 -> false
      o1 is JBIterable<*> && o2 is JBIterable<*> -> equalData(o1.toList(), o2.toList(), level + 1, set())
      else -> {
        for (field in ReflectionUtil.collectFields(c1)) {
          if (Modifier.isStatic(field.modifiers)) continue
          field.isAccessible = true
          val o11 = field.get(o1)
          val o22 = field.get(o2)
          if (!equalData(o11, o22, level + 1, set())) return false
        }
        true
      }
    }
  }

  private fun isSpecialContextComponentOnlyKey(key: DataKey<*>): Boolean {
    return key === PlatformCoreDataKeys.CONTEXT_COMPONENT ||
           key === PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
           key === PlatformDataKeys.MODALITY_STATE ||
           key === PlatformDataKeys.SPEED_SEARCH_TEXT ||
           key === PlatformDataKeys.SPEED_SEARCH_COMPONENT
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

  private class ContextData(val key: String,
                            val valueStr: String,
                            val valueClass: String,
                            val overridden: Boolean)

  private class Header(val key: String) {
    override fun toString(): String = key
  }
}