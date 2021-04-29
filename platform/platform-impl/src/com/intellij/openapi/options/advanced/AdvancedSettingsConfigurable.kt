// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

 import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UiDslConfigurable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.layout.*
import com.intellij.util.Alarm
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class AdvancedSettingsConfigurable : UiDslConfigurable.Simple(), SearchableConfigurable {
  private class SettingsRow(val row: Row, val text: String)

  private val settingsRows = mutableListOf<SettingsRow>()

  private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)

  private val searchField = SearchTextField().apply {
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest(Runnable { updateSearch() }, 300)
      }
    })
  }

  override fun RowBuilder.createComponentRow() {
    row {
      searchField(CCFlags.growX)
    }

    val groupedExtensions = AdvancedSettingBean.EP_NAME.extensions.groupBy {
      it.group() ?: ApplicationBundle.message("group.advanced.settings.other")
    }

    for ((group, extensions) in groupedExtensions) {
      titledRow(group) {
        for (extension in extensions) {
          val row = row {
            control(extension).also {
              extension.description()?.let { description -> it.comment(description) }
            }
          }
          settingsRows.add(SettingsRow(row, extension.title()))
        }
      }
    }
  }

  private fun Row.control(extension: AdvancedSettingBean): CellBuilder<JComponent> {
    return when (extension.type()) {
      AdvancedSettingType.Bool ->
        checkBox(
          extension.title(),
          { AdvancedSettings.getBoolean(extension.id) },
          { AdvancedSettings.setBoolean(extension.id, it) }
        )

      AdvancedSettingType.Int -> {
        label(extension.title() + ":")
        intTextField(
          { AdvancedSettings.getInt(extension.id) },
          { AdvancedSettings.setInt(extension.id, it) },
          columns = 10
        )
      }

      AdvancedSettingType.String -> {
        label(extension.title() + ":")
        textField(
          { AdvancedSettings.getString(extension.id) },
          { AdvancedSettings.setString(extension.id, it) },
          columns = 20
        )
      }
    }
  }

  private fun updateSearch() {
    applyFilter(searchField.text)
  }

  private fun applyFilter(text: String?) {
    if (text.isNullOrBlank()) {
      for (settingsRow in settingsRows) {
        settingsRow.row.visible = true
        settingsRow.row.subRowsVisible = true
      }
      return
    }

    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val filterWords = searchableOptionsRegistrar.getProcessedWords(text)
    for (settingsRow in settingsRows) {
      val textWords = searchableOptionsRegistrar.getProcessedWords(settingsRow.text)
      val matches = textWords.containsAll(filterWords)
      settingsRow.row.visible = matches
      settingsRow.row.subRowsVisible = matches
    }
  }

  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun enableSearch(option: String?): Runnable {
    return Runnable { applyFilter(option) }
  }
}
