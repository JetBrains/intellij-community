// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.components

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

internal class ListPersistentStateComponentsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ComponentListDialog().show()
  }

  class ComponentListDialog : DialogWrapper(false) {
    init {
      init()
      title = "Application-Level Persistent State Components"
      setSize(JBUI.scale(1000), JBUI.scale(600))
    }

    override fun createCenterPanel(): JComponent {
      val componentTable = JBTable()
      componentTable.model = ComponentTableModel()
      ComponentTableModel.columnWidths.forEachIndexed { i, width ->
        if (width > 0) {
          val column = componentTable.columnModel.getColumn(i)
          column.minWidth = JBUI.scale(width)
          column.maxWidth = JBUI.scale(width)
        }
      }
      return panel {
        row {
          scrollPane(componentTable)
        }
      }
    }

    class ComponentTableModel : AbstractTableModel() {

      companion object {
        val columnNames = arrayOf("Plugin", "Class Name", "Roaming Type", "Category")
        val columnWidths = arrayOf(250, -1, 100, 100)
      }

      private val descriptors = ArrayList<ComponentDescriptor>()

      init {
        val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
        componentManager.processAllImplementationClasses { aClass, descriptor ->
          if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
            val state = aClass.getAnnotation(State::class.java)
            val roamingType = getRoamingType(state)
            @Suppress("UNCHECKED_CAST")
            descriptors.add(
              ComponentDescriptor(
                descriptor?.name?.toString() ?: "",
                aClass.name,
                roamingType,
                getCategory(aClass as Class<PersistentStateComponent<*>>, state, descriptor, roamingType)
              )
            )
          }
        }
        descriptors.sortWith(
          compareBy<ComponentDescriptor> { it.name }.thenBy { it.className }
        )
      }

      private fun getCategory(aClass : Class<PersistentStateComponent<*>>, state: State?, descriptor: PluginDescriptor?, roamingType: String): String {
        if (roamingType != RoamingType.DISABLED.toString() && descriptor != null) {
          if (descriptor.name == PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.idString) {
            return state?.category?.name ?: ""
          }
          else {
            return ComponentCategorizer.getPluginCategory(aClass, descriptor).toString()
          }
        }
        else {
          return ""
        }
      }

      private fun getRoamingType(state: State?): String {
        if (state != null) {
          var roamingType: String? = null
          state.storages.forEach {
            if (!it.deprecated) {
              val storageRoamingType =
                if (it.value == StoragePathMacros.NON_ROAMABLE_FILE ||
                    it.value == StoragePathMacros.CACHE_FILE ||
                    it.value == StoragePathMacros.WORKSPACE_FILE) "DISABLED"
                else it.roamingType.toString()
              if (roamingType == null) {
                roamingType = storageRoamingType
              }
              else {
                if (roamingType != storageRoamingType) {
                  roamingType = "MIXED"
                }
              }
            }
          }
          return roamingType ?: ""
        }
        return ""
      }

      override fun getRowCount() = descriptors.size

      override fun getColumnCount() = 4

      override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return when (columnIndex) {
          0 -> descriptors[rowIndex].name
          1 -> descriptors[rowIndex].className
          2 -> descriptors[rowIndex].roamingType
          3 -> descriptors[rowIndex].category
          else -> ""
        }
      }

      override fun getColumnName(column: Int): String {
        return columnNames[column]
      }
    }
  }

  data class ComponentDescriptor(
    val name: String,
    val className: String,
    val roamingType: String,
    val category: String
  )
}