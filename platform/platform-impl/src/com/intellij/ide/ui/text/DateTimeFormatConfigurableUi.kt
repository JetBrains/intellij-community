// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.DateTimeFormatterBean
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

/**
 * @author Konstantin Bulenkov
 */
class DateTimeFormatConfigurableUi(settings: DateTimeFormatManager) : ConfigurableUi<DateTimeFormatManager> {
  val ui: JComponent

  init {
    ui = panel {
      row {
        checkBox("Allow pretty formatting",
                 { settings.isPrettyFormattingAllowed },
                 { settings.setAllowPrettyFormattingGlobally(it) })
      }
      row {
        cell(isFullWidth = true) {
          scrollPane(JBTable(createModel(settings)))
        }
      }
    }
  }


  private fun createModel(settings: DateTimeFormatManager): TableModel? {
    val formatters = DateTimeFormatterBean.EP_NAME.getExtensionList(null).map { it.id to it }.toMap()
    val ids = formatters.keys.toMutableList()
    ids.sort()
    val values = ids.map { it to settings.getDateFormatPattern(it) }.toMap()

    return object : DefaultTableModel(ids.size, 2) {
      override fun isCellEditable(row: Int, column: Int): Boolean = column != 0

      override fun getColumnName(column: Int): String? = when (column) {
        0 -> "Name"
        else -> "Formatter"
      }

      override fun getValueAt(row: Int, column: Int): Any? = when (column) {
        0 -> formatters[ids[row]]?.name
        else -> values[ids[row]]
      }
    }
  }

  override fun reset(settings: DateTimeFormatManager) {

  }

  override fun isModified(settings: DateTimeFormatManager): Boolean {
    return false
  }

  @Throws(ConfigurationException::class)
  override fun apply(settings: DateTimeFormatManager) {

  }

  override fun getComponent(): JComponent {
    return ui
  }
}
