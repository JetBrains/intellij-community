// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.parseUiThemeValue
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.speedSearch.FilteringTableModel
import com.intellij.ui.table.JBTable
import java.awt.Color
import javax.swing.UIManager

internal class ShowUIDefaultsContent(@JvmField val table: JBTable) {
  companion object {
    const val LAST_SELECTED_KEY = "LaFDialog.lastSelectedElement"
    private const val COLORS_ONLY_KEY = "LaFDialog.ColorsOnly"
    private const val SEARCH_FIELD_HISTORY_KEY = "LaFDialog.filter"
  }

  lateinit var searchField: JBTextField
  private lateinit var colorsOnly: JBCheckBox

  @JvmField
  val panel = panel {
    row(IdeBundle.message("label.ui.filter")) {
      searchField = textField()
        .columns(40)
        .text(PropertiesComponent.getInstance().getValue(SEARCH_FIELD_HISTORY_KEY, ""))
        .onChanged { updateFilter() }
        .align(AlignX.FILL)
        .focused()
        .component
    }
    row {
      val pane = ToolbarDecorator.createDecorator(table)
        .setToolbarPosition(ActionToolbarPosition.BOTTOM)
        .setAddAction(AnActionButtonRunnable { _ -> addNewValue() })
        .createPanel()
      cell(pane)
        .align(Align.FILL)
    }.resizableRow()
    row {
      colorsOnly = checkBox(IdeBundle.message("checkbox.colors.only"))
        .selected(PropertiesComponent.getInstance().getBoolean(COLORS_ONLY_KEY, false))
        .onChanged { updateFilter() }
        .component
    }
  }

  init {
    ScrollingUtil.installActions(table, true, searchField)
    updateFilter()
    restoreLastSelected()
  }

  fun storeState() {
    PropertiesComponent.getInstance().setValue(SEARCH_FIELD_HISTORY_KEY, searchField.getText())
    PropertiesComponent.getInstance().setValue(COLORS_ONLY_KEY, colorsOnly.isSelected, false)
    val selected = table.getValueAt(table.selectedRow, 0)
    if (selected is Pair<*, *>) {
      PropertiesComponent.getInstance().setValue(LAST_SELECTED_KEY, selected.first.toString())
    }
  }

  private fun addNewValue() {
    ApplicationManager.getApplication().invokeLater(Runnable {
      ShowUIDefaultsAddValue(table, true) { name, value ->
        val trimmedKey = name.trim()
        val trimmedValue = value.trim()
        if (!trimmedKey.isEmpty() && !trimmedValue.isEmpty()) {
          UIManager.put(trimmedKey, parseUiThemeValue(key = trimmedKey,
                                                      value = trimmedValue,
                                                      classLoader = LafManager.getInstance().currentUIThemeLookAndFeel.providerClassLoader,
                                                      warn = { message, throwable ->
                                                        thisLogger().warn(message, throwable)
                                                      }))
          table.setModel(ShowUIDefaultsAction.createFilteringModel())
          updateFilter()
        }
      }.show()
    })
  }

  private fun updateFilter() {
    val model = table.model as FilteringTableModel<*>
    if (StringUtil.isEmpty(searchField.getText()) && !colorsOnly.isSelected) {
      model.setFilter(null)
      return
    }
    val matcher = NameUtil.buildMatcher("*" + searchField.getText(), NameUtil.MatchingCaseSensitivity.NONE)
    model.setFilter { pair ->
      val obj = (pair as Pair<*, *>).second
      var value = when (obj) {
        null -> "null"
        is Color -> ColorUtil.toHtmlColor(obj)
        else -> obj.toString()
      }
      value = pair.first.toString() + " " + value
      (!colorsOnly.isSelected || obj is Color) && matcher.matches(value)
    }
  }

  private fun restoreLastSelected() {
    val key = PropertiesComponent.getInstance().getValue(LAST_SELECTED_KEY)
    if (key != null) {
      for (i in 0 until table.getRowCount()) {
        val valueAt = table.model.getValueAt(i, 0)
        if (valueAt is Pair<*, *> && key == valueAt.first) {
          table.selectionModel.leadSelectionIndex = i
          table.selectionModel.setSelectionInterval(i, i)
          ScrollingUtil.ensureIndexIsVisible(table, i, 1)
          return
        }
      }
      ScrollingUtil.ensureSelectionExists(table)
    }
  }
}
