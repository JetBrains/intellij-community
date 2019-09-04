// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.DateTimeFormatterBean
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

/**
 * @author Konstantin Bulenkov
 */
class DateTimeFormatConfigurableUi(settings: DateTimeFormatManager) : ConfigurableUi<DateTimeFormatManager> {
  private val ui: JComponent
  private val formattersTable: JTable
  private lateinit var allowPrettyFormatting: JCheckBox
  private val patterns: MutableMap<String, String?>
  private val formatterIds: MutableList<String>

  private val formatters: Map<String, DateTimeFormatterBean> =
    DateTimeFormatterBean.EP_NAME.getExtensionList(null).map { it.id to it }.toMap()

  init {
    formatterIds = formatters.keys.toMutableList()
    formatterIds.sort()
    patterns = formatterIds.map { it to settings.getDateFormatPattern(it) }.toMap().toMutableMap()
    formattersTable = JBTable(createModel(settings))
    ui = panel {
      row {
        allowPrettyFormatting = checkBox("Allow pretty formatting",
                                         { settings.isPrettyFormattingAllowed },
                                         { settings.isPrettyFormattingAllowed = it }).component
      }
      row {
        scrollPane(formattersTable).noGrowY()
        commentRow("<html>Use Date and Time patterns to change date format. Example, <b>yyyy.MM.dd G 'at' HH:mm:ss z</b> For more examples visit <a href='https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html'>the documentation page</a>.</html>")
      }
    }
  }

  private fun createModel(settings: DateTimeFormatManager): TableModel? {
    return object : DefaultTableModel(formatterIds.size, 2) {
      override fun isCellEditable(row: Int, column: Int): Boolean = column != 0

      override fun getColumnName(column: Int): String? = when (column) {
        0 -> "Area"
        else -> "Date Time Pattern"
      }

      override fun getValueAt(row: Int, column: Int): Any? = when (column) {
        0 -> formatters[formatterIds[row]]?.name
        else -> patterns[formatterIds[row]]
      }

      override fun setValueAt(aValue: Any?, row: Int, column: Int) {
        patterns[formatterIds[row]] = aValue?.toString()
      }
    }
  }

  override fun reset(settings: DateTimeFormatManager) {
    allowPrettyFormatting.isSelected = settings.isPrettyFormattingAllowed

    formatterIds.forEach {
      patterns[it] = settings.getDateFormatPattern(it)
    }
    formattersTable.revalidate()
  }

  override fun isModified(settings: DateTimeFormatManager): Boolean {
    if (allowPrettyFormatting.isSelected != settings.isPrettyFormattingAllowed) {
      return true
    }

    for (id in formatterIds) {
      val newValue = patterns[id]
      val oldValue = settings.getDateFormatPattern(id)
      if (!((StringUtil.isEmpty(newValue) && StringUtil.isEmpty(oldValue)) || StringUtil.equals(newValue, oldValue))) {
        return true
      }
    }
    return false
  }

  @Throws(ConfigurationException::class)
  override fun apply(settings: DateTimeFormatManager) {
    settings.isPrettyFormattingAllowed = allowPrettyFormatting.isSelected

    formatterIds.forEach {
      settings.setDateFormatPattern(it, patterns[it])
    }

    LafManager.getInstance().updateUI()
  }

  override fun getComponent(): JComponent {
    return ui
  }
}
