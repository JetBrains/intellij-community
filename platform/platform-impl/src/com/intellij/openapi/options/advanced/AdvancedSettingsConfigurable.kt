// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

class AdvancedSettingsConfigurable : DslConfigurableBase(), SearchableConfigurable, Configurable.NoScroll {

  private class SettingsGroup(val groupRow: Row,
                              val title: String,
                              val settingsRows: Collection<SettingsRow>)

  private class SettingsRow(val row: Row,
                            val component: JComponent,
                            val id: String,
                            val text: String,
                            val isDefaultPredicate: ComponentPredicate) {
    fun setVisible(visible: Boolean) {
      row.visible(visible)
    }
  }

  private val settingsGroups = mutableListOf<SettingsGroup>()
  private lateinit var nothingFoundRow: Row
  private var onlyShowModified = false

  private val searchAlarm = Alarm()

  private val searchField = SearchTextField().apply {
    textEditor.emptyText.text = ApplicationBundle.message("search.advanced.settings")

    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest(Runnable { updateSearch() }, 300)
      }
    })
  }

  override fun createPanel(): DialogPanel {
    val extensionsSettings = createExtensionsSettings()
    val result = panel {
      row {
        cell(searchField)
          .horizontalAlign(HorizontalAlign.FILL)
          .resizableColumn()
        checkBox(ApplicationBundle.message("checkbox.advanced.settings.modified"))
          .actionListener { _, component ->
            onlyShowModified = component.isSelected
            updateSearch()
          }
      }.layout(RowLayout.INDEPENDENT)

      nothingFoundRow = row {
        label(ApplicationBundle.message("search.advanced.settings.nothing.found"))
          .horizontalAlign(HorizontalAlign.CENTER)
          .verticalAlign(VerticalAlign.CENTER)
          .applyToComponent {
            foreground = UIUtil.getInactiveTextColor()
          }
      }.visible(false)

      row {
        val scrollable = ScrollPaneFactory.createScrollPane(extensionsSettings, true)
        scrollable.preferredSize = Dimension(JBUI.scale(300), JBUI.scale(200))
        cell(scrollable)
          .horizontalAlign(HorizontalAlign.FILL)
          .verticalAlign(VerticalAlign.FILL)
      }.resizableRow()
    }
    result.registerSubPanel(extensionsSettings)
    return result
  }

  private fun createExtensionsSettings(): DialogPanel {
    val groupedExtensions = AdvancedSettingBean.EP_NAME.extensions.groupBy {
      it.group() ?: ApplicationBundle.message("group.advanced.settings.other")
    }.toSortedMap()

    return panel {
      for ((group, extensions) in groupedExtensions) {
        val settingsRows = mutableListOf<SettingsRow>()
        val groupRow = group(title = group) {
          for (extension in extensions) {
            val label = if (extension.type() == AdvancedSettingType.Bool)
              null
            else
              JLabel(extension.title() + ":")
            lateinit var advancedSetting: AdvancedSettingControl
            val settingRow = row(label) {
              advancedSetting = control(extension)
              extension.trailingLabel()?.takeIf { it.isNotEmpty() }?.let {
                label(it)
                  .gap(RightGap.SMALL)
              }

              val resetAction = object : DumbAwareAction(ApplicationBundle.message("button.advanced.settings.reset"), null, AllIcons.Diff.Revert) {
                override fun actionPerformed(e: AnActionEvent) {
                  advancedSetting.reset()
                }
              }
              val minSize = AllIcons.Diff.Revert.iconHeight + 4 // Add space for border
              actionButton(resetAction)
                .applyToComponent {
                  setMinimumButtonSize(Dimension(minSize, minSize))
                }
                .visibleIf(advancedSetting.isDefault.not())
            }
            extension.description()?.let {
              if (label == null) {
                advancedSetting.cellBuilder.comment(it)
              } else {
                settingRow.rowComment(it)
              }
            }

            val row = SettingsRow(
              settingRow, label ?: advancedSetting.cellBuilder.component, extension.id,
              label?.text ?: extension.title(),
              advancedSetting.isDefault
            )
            settingsRows.add(row)
          }
        }

        settingsGroups.add(SettingsGroup(groupRow, group, settingsRows))
      }
    }
  }

  data class AdvancedSettingControl(val cellBuilder: Cell<JComponent>, val isDefault: ComponentPredicate, val reset: () -> Unit)

  private fun Row.control(extension: AdvancedSettingBean): AdvancedSettingControl {
    val result = when (extension.type()) {
      AdvancedSettingType.Bool -> {
        val cb = checkBox(extension.title())
          .bindSelected({ AdvancedSettings.getBoolean(extension.id) }, { AdvancedSettings.setBoolean(extension.id, it) })
        AdvancedSettingControl(
          cb,
          if (extension.defaultValueObject == true) cb.component.selected else cb.component.selected.not()
        ) { cb.component.isSelected = extension.defaultValueObject as Boolean }
      }

      AdvancedSettingType.Int -> {
        val textField = intTextField()
          .bindIntText({ AdvancedSettings.getInt(extension.id) }, { AdvancedSettings.setInt(extension.id, it) })
        AdvancedSettingControl(
          textField,
          textField.component.enteredTextSatisfies { it == extension.defaultValueObject.toString() }
        ) { textField.component.text = extension.defaultValueObject.toString() }
      }

      AdvancedSettingType.String -> {
        val textField = textField()
          .columns(30)
          .bindText({ AdvancedSettings.getString(extension.id) }, { AdvancedSettings.setString(extension.id, it) })
        AdvancedSettingControl(
          textField,
          textField.component.enteredTextSatisfies { it == extension.defaultValueObject }
        ) { textField.component.text = extension.defaultValueObject as String }
      }

      AdvancedSettingType.Enum -> {
        val comboBoxModel = CollectionComboBoxModel(extension.enumKlass!!.enumConstants.toList())
        val cb = comboBox(comboBoxModel)
          .bindItem(
            { AdvancedSettings.getEnum(extension.id, extension.enumKlass!!) },
            { AdvancedSettings.setEnum(extension.id, it as Enum<*>) }
          )
        AdvancedSettingControl(
          cb,
          cb.component.selectedValueIs(extension.defaultValueObject as Enum<*>)
        ) { cb.component.selectedItem = extension.defaultValueObject }
      }
    }

    result.cellBuilder.gap(RightGap.SMALL)
    return result
  }

  private fun updateSearch() {
    applyFilter(searchField.text, onlyShowModified)
  }

  private fun applyFilter(searchText: String?, onlyShowModified: Boolean) {
    if (searchText.isNullOrBlank() && !onlyShowModified) {
      for (groupPanel in settingsGroups) {
        groupPanel.groupRow.visible(true)
        for (settingsRow in groupPanel.settingsRows) {
          settingsRow.setVisible(true)
          updateMatchText(settingsRow.component, settingsRow.text, null)
        }
      }
      nothingFoundRow.visible(false)
      return
    }

    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val filterWords = searchText?.let { searchableOptionsRegistrar.getProcessedWords(it) } ?: emptySet()
    val filterWordsUnstemmed = searchText?.split(' ') ?: emptySet()
    var matchCount = 0

    for (settingsGroup in settingsGroups) {
      settingsGroup.groupRow.visible(true)
      var groupVisible = false
      if (!onlyShowModified && isMatch(filterWords, settingsGroup.title)) {
        matchCount++
        groupVisible = true
      }

      for (settingsRow in settingsGroup.settingsRows) {
        val idWords = settingsRow.id.split('.')
        val textMatches = searchText == null || isMatch(filterWords, settingsRow.text)
        val idMatches = searchText == null || (filterWordsUnstemmed.isNotEmpty() && idWords.containsAll(filterWordsUnstemmed))
        val modifiedMatches = if (onlyShowModified) !settingsRow.isDefaultPredicate() else true
        val matches = (textMatches || idMatches) && modifiedMatches
        settingsRow.setVisible(matches)
        if (matches) {
          matchCount++
          groupVisible = true
          val idColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
          val baseText = if (idMatches && !textMatches)
            """${settingsRow.text}<br><pre><font color="$idColor">${settingsRow.id}"""
          else
            settingsRow.text
          updateMatchText(settingsRow.component, baseText, searchText)
        }
      }

      if (!groupVisible) {
        settingsGroup.groupRow.visible(false)
      }
    }

    nothingFoundRow.visible(matchCount == 0)
    SettingsCounterUsagesCollector.ADVANDED_SETTINGS_SEARCH.log(matchCount, searchText?.length ?: 0, onlyShowModified)
  }

  private fun isMatch(filterWords: Collection<String>, text: String): Boolean {
    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val textWords = searchableOptionsRegistrar.getProcessedWords(text)

    for (filterWord in filterWords) {
      if (!textWords.contains(filterWord) && !text.toLowerCase().contains(filterWord.toLowerCase())) {
        return false
      }
    }
    return true
  }

  companion object {
    fun updateMatchText(component: JComponent, @NlsSafe baseText: String, @NlsSafe searchText: String?) {
      val textColor = JBColor(Gray._50, Gray._0) // Same color as in SimpleColoredComponent.doPaintText
      val text = searchText?.takeIf { it.isNotBlank() }?.let {
        @NlsSafe val highlightedText = SearchUtil.markup(baseText, it, textColor, UIUtil.getSearchMatchGradientStartColor())
        "<html>$highlightedText"
      } ?: baseText
      when (component) {
        is JLabel -> component.text = text
        is AbstractButton -> component.text = text
      }
    }
  }
  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun getHelpTopic(): String = "Advanced_settings"

  override fun enableSearch(option: String?): Runnable {
    return Runnable { applyFilter(option, false) }
  }
}
