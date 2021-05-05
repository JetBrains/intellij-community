// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

 import com.intellij.ide.ui.search.SearchUtil
 import com.intellij.ide.ui.search.SearchableOptionsRegistrar
 import com.intellij.openapi.application.ApplicationBundle
 import com.intellij.openapi.options.SearchableConfigurable
 import com.intellij.openapi.options.UiDslConfigurable
 import com.intellij.openapi.util.NlsSafe
 import com.intellij.ui.DocumentAdapter
 import com.intellij.ui.SearchTextField
 import com.intellij.ui.layout.*
 import com.intellij.util.Alarm
 import com.intellij.util.ui.UIUtil
 import javax.swing.AbstractButton
 import javax.swing.JComponent
 import javax.swing.JLabel
 import javax.swing.SwingConstants
 import javax.swing.event.DocumentEvent

class AdvancedSettingsConfigurable : UiDslConfigurable.Simple(), SearchableConfigurable {
  private class SettingsRow(val row: Row, val component: JComponent, val text: String, val groupRow: Row)

  private val settingsRows = mutableListOf<SettingsRow>()
  private val groupRows = mutableListOf<Row>()
  private lateinit var nothingFoundRow: Row

  private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)

  private val searchField = SearchTextField().apply {
    textEditor.emptyText.text = ApplicationBundle.message("search.advanced.settings")

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
    }.toSortedMap()

    for ((group, extensions) in groupedExtensions) {
      titledRow(group) {
        val groupRow = this
        groupRows.add(groupRow)

        for (extension in extensions) {
          row {
            val (label, component) = control(extension)
            extension.description()?.let { description -> component.comment(description) }
            val textComponent = label?.component ?: component.component
            settingsRows.add(SettingsRow(this, textComponent, extension.title(), groupRow))
          }
        }
      }
    }

    nothingFoundRow = row {
      label(ApplicationBundle.message("search.advanced.settings.nothing.found"))
        .constraints(CCFlags.growX, CCFlags.growY)
        .also {
          it.component.foreground = UIUtil.getInactiveTextColor()
          it.component.horizontalAlignment = SwingConstants.CENTER
        }
    }.also {
      it.visible = false
    }
  }

  private fun Row.control(extension: AdvancedSettingBean): Pair<CellBuilder<JLabel>?, CellBuilder<JComponent>> {
    return when (extension.type()) {
      AdvancedSettingType.Bool ->
        null to checkBox(
          extension.title(),
          { AdvancedSettings.getBoolean(extension.id) },
          { AdvancedSettings.setBoolean(extension.id, it) }
        )

      AdvancedSettingType.Int -> {
        label(extension.title() + ":") to intTextField(
          { AdvancedSettings.getInt(extension.id) },
          { AdvancedSettings.setInt(extension.id, it) },
          columns = 10
        )
      }

      AdvancedSettingType.String -> {
        label(extension.title() + ":") to textField(
          { AdvancedSettings.getString(extension.id) },
          { AdvancedSettings.setString(extension.id, it) },
          columns = 20
        )
      }

      AdvancedSettingType.Enum -> {
        val builder = label(extension.title() + ":")
        cell {
          buttonGroup(
            { AdvancedSettings.getEnum(extension.id, extension.enumKlass!!) },
            { AdvancedSettings.setEnum(extension.id, it) }
          ) {
            for (enumConstant in extension.enumKlass!!.enumConstants) {
              radioButton(enumConstant.toString(), enumConstant)
            }
          }
        }
        builder to builder
      }
    }
  }

  private fun updateSearch() {
    applyFilter(searchField.text)
  }

  private fun applyFilter(searchText: String?) {
    if (searchText.isNullOrBlank()) {
      for (groupRow in groupRows) {
        groupRow.visible = true
        groupRow.subRowsVisible = true
      }
      for (settingsRow in settingsRows) {
        settingsRow.row.visible = true
        settingsRow.row.subRowsVisible = true
        updateMatchText(settingsRow.component, settingsRow.text, null)
      }
      nothingFoundRow.visible = false
      return
    }

    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val filterWords = searchableOptionsRegistrar.getProcessedWords(searchText)
    val visibleGroupRows = mutableSetOf<Row>()
    for (settingsRow in settingsRows) {
      val textWords = searchableOptionsRegistrar.getProcessedWords(settingsRow.text)
      val matches = textWords.containsAll(filterWords)
      settingsRow.row.visible = matches
      settingsRow.row.subRowsVisible = matches
      if (matches) {
        settingsRow.groupRow.visible = true
        settingsRow.groupRow.subRowsVisible = true
        visibleGroupRows.add(settingsRow.groupRow)
        updateMatchText(settingsRow.component, settingsRow.text, searchText)
      }
    }
    for (groupRow in groupRows) {
      if (groupRow !in visibleGroupRows) {
        groupRow.visible = false
        groupRow.subRowsVisible = false
      }
    }
    nothingFoundRow.visible = visibleGroupRows.isEmpty()
  }

  private fun updateMatchText(component: JComponent, @NlsSafe baseText: String, @NlsSafe searchText: String?) {
    val text = searchText?.let {
      "<html>" + SearchUtil.markup(baseText, it, UIUtil.getLabelFontColor(UIUtil.FontColor.NORMAL), UIUtil.getSearchMatchGradientStartColor())
    } ?: baseText
    when (component) {
      is JLabel -> component.text = text
      is AbstractButton -> component.text = text
    }
  }

  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun enableSearch(option: String?): Runnable {
    return Runnable { applyFilter(option) }
  }
}
