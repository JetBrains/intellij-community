// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.components

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.event.ActionEvent
import java.io.File
import java.io.FileOutputStream
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

internal class ListPersistentStateComponentsAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
          scrollCell(componentTable)
            .align(Align.FILL)
        }.resizableRow()
      }
    }

    override fun createActions(): Array<Action> = arrayOf(
      object : AbstractAction("To Json Model...") {
        override fun actionPerformed(e: ActionEvent?) {
          chooseTargetFile()?.let { target ->
            val model = buildSettingsModel()
            val json = model.toJson()
            FileOutputStream(target).use { os ->
              os.write(json.toByteArray(Charsets.UTF_8))
            }
          }
        }
      },
      okAction
    )

    internal fun chooseTargetFile(): File? {
      val saveDialog = FileChooserFactory.getInstance()
        .createSaveFileDialog(FileSaverDescriptor(
          "Export to Json",
          "Choose target file",
          "json"), null)
      val target = saveDialog.save(null)
      return target?.file
    }

    class ComponentTableModel : AbstractTableModel() {

      companion object {
        val columnNames: Array<String> = arrayOf("Plugin", "Class Name", "Roaming Type", "Category")
        val columnWidths: Array<Int> = arrayOf(250, -1, 100, 100)
      }

      private val model = buildSettingsModel()

      override fun getRowCount(): Int = model.components.size

      override fun getColumnCount(): Int = 4

      override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return when (columnIndex) {
          0 -> model.components[rowIndex].name
          1 -> model.components[rowIndex].aClass.name
          2 -> model.components[rowIndex].getRoamingTypeString()
          3 -> model.components[rowIndex].getCategoryString()
          else -> ""
        }
      }

      override fun getColumnName(column: Int): String {
        return columnNames[column]
      }
    }
  }


}