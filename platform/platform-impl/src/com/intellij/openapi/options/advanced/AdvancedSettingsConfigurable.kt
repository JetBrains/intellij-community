// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

 import com.intellij.icons.AllIcons
 import com.intellij.ide.ui.search.SearchUtil
 import com.intellij.ide.ui.search.SearchableOptionsRegistrar
 import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector
 import com.intellij.openapi.actionSystem.AnActionEvent
 import com.intellij.openapi.application.ApplicationBundle
 import com.intellij.openapi.options.SearchableConfigurable
 import com.intellij.openapi.options.UiDslConfigurable
 import com.intellij.openapi.project.DumbAwareAction
 import com.intellij.openapi.util.NlsSafe
 import com.intellij.ui.CollectionComboBoxModel
 import com.intellij.ui.ColorUtil
 import com.intellij.ui.DocumentAdapter
 import com.intellij.ui.SearchTextField
 import com.intellij.ui.layout.*
 import com.intellij.util.Alarm
 import com.intellij.util.ui.JBUI
 import com.intellij.util.ui.UIUtil
 import java.awt.Dimension
 import javax.swing.*
 import javax.swing.event.DocumentEvent

class AdvancedSettingsConfigurable : UiDslConfigurable.Simple(), SearchableConfigurable {
  private class SettingsRow(val row: Row,
                            val component: JComponent,
                            val id: String,
                            val text: String,
                            val isDefaultPredicate: ComponentPredicate) {
    lateinit var groupPanel: JPanel
  }

  private val settingsRows = mutableListOf<SettingsRow>()
  private val groupPanels = mutableListOf<JPanel>()
  private lateinit var nothingFoundRow: Row
  private var onlyShowModified = false

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
      cell {
        searchField(CCFlags.growX)
        checkBox(ApplicationBundle.message("checkbox.advanced.settings.modified")) { _, component ->
          onlyShowModified = component.isSelected
          updateSearch()
        }
      }
    }

    val groupedExtensions = AdvancedSettingBean.EP_NAME.extensions.groupBy {
      it.group() ?: ApplicationBundle.message("group.advanced.settings.other")
    }.toSortedMap()

    for ((group, extensions) in groupedExtensions) {
      val settingsRowsInGroup = mutableListOf<SettingsRow>()
      val groupPanel = nestedPanel {
        titledRow(group) {
          for (extension in extensions) {
            row {
              val labelCellBuilder = if (extension.type() == AdvancedSettingType.Bool)
                null
              else
                label(extension.title() + ":")

              lateinit var component: CellBuilder<JComponent>
              cell(isFullWidth = true) {
                val (c, isDefaultPredicate, reset) = control(extension)
                component = c
                extension.trailingLabel()?.takeIf { it.isNotEmpty() }?.let { label(it) }

                val resetAction = object : DumbAwareAction(ApplicationBundle.message("button.advanced.settings.reset"), null, AllIcons.Diff.Revert) {
                  override fun actionPerformed(e: AnActionEvent) {
                    reset()
                  }
                }
                val minSize = AllIcons.Diff.Revert.iconHeight
                actionButton(resetAction, Dimension(minSize, minSize))
                  .visibleIf(isDefaultPredicate.not())

                label("").constraints(pushX)

                val textComponent = labelCellBuilder?.component ?: component.component
                val row = SettingsRow(
                  this@row, textComponent, extension.id, labelCellBuilder?.component?.text ?: extension.title(),
                  isDefaultPredicate
                )
                settingsRows.add(row)
                settingsRowsInGroup.add(row)
              }

              extension.description()?.let { description -> component.comment(description) }
            }
          }
        }
      }.component
      groupPanels.add(groupPanel)
      for (settingsRow in settingsRowsInGroup) {
        settingsRow.groupPanel = groupPanel
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

  data class AdvancedSettingControl(val cellBuilder: CellBuilder<JComponent>, val isDefault: ComponentPredicate, val reset: () -> Unit)

  private fun InnerCell.control(extension: AdvancedSettingBean): AdvancedSettingControl {
    return when (extension.type()) {
      AdvancedSettingType.Bool -> {
        val cb = checkBox(
          extension.title(),
          { AdvancedSettings.getBoolean(extension.id) },
          { AdvancedSettings.setBoolean(extension.id, it) }
        )
        AdvancedSettingControl(
          cb,
          if (extension.defaultValueObject == true) cb.component.selected else cb.component.selected.not(),
          { cb.component.isSelected = extension.defaultValueObject as Boolean }
        )
      }

      AdvancedSettingType.Int -> {
        val textField = intTextField(
          { AdvancedSettings.getInt(extension.id) },
          { AdvancedSettings.setInt(extension.id, it) },
          columns = 10
        )
        AdvancedSettingControl(
          textField,
          textField.component.enteredTextSatisfies { it == extension.defaultValueObject.toString() },
          { textField.component.text = extension.defaultValueObject.toString() }
        )
      }

      AdvancedSettingType.String -> {
        val textField = textField(
          { AdvancedSettings.getString(extension.id) },
          { AdvancedSettings.setString(extension.id, it) },
          columns = 30
        )
        AdvancedSettingControl(
          textField,
          textField.component.enteredTextSatisfies { it == extension.defaultValueObject },
          { textField.component.text = extension.defaultValueObject as String }
        )
      }

      AdvancedSettingType.Enum -> {
        val comboBoxModel = CollectionComboBoxModel(extension.enumKlass!!.enumConstants.toList())
        val cb = comboBox(
          comboBoxModel,
          { AdvancedSettings.getEnum(extension.id, extension.enumKlass!!) },
          { AdvancedSettings.setEnum(extension.id, it as Enum<*>) }
        )
        AdvancedSettingControl(
          cb,
          cb.component.selectedValueIs(extension.defaultValueObject as Enum<*>),
          { cb.component.selectedItem = extension.defaultValueObject }
        )
      }
    }
  }

  private fun updateSearch() {
    applyFilter(searchField.text, onlyShowModified)
  }

  private fun applyFilter(searchText: String?, onlyShowModified: Boolean) {
    if (searchText.isNullOrBlank() && !onlyShowModified) {
      for (groupPanel in groupPanels) {
        groupPanel.isVisible = true
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
    val filterWords = searchText?.let { searchableOptionsRegistrar.getProcessedWords(it) } ?: emptySet()
    val filterWordsUnstemmed = searchText?.split(' ') ?: emptySet()
    val visibleGroupPanels = mutableSetOf<JPanel>()
    var matchCount = 0
    for (settingsRow in settingsRows) {
      val textWords = searchableOptionsRegistrar.getProcessedWords(settingsRow.text)
      val idWords = settingsRow.id.split('.')
      val textMatches = searchText == null || (filterWords.isNotEmpty() && textWords.containsAll(filterWords))
      val idMatches = searchText == null || (filterWordsUnstemmed.isNotEmpty() && idWords.containsAll(filterWordsUnstemmed))
      val modifiedMatches = if (onlyShowModified) !settingsRow.isDefaultPredicate() else true
      val matches = (textMatches || idMatches) && modifiedMatches
      if (matches) matchCount++

      settingsRow.row.visible = matches
      settingsRow.row.subRowsVisible = matches
      if (matches) {
        settingsRow.groupPanel.isVisible = true
        visibleGroupPanels.add(settingsRow.groupPanel)
        val idColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
        val baseText = if (idMatches && !textMatches)
          """${settingsRow.text}<br><pre><font color="$idColor">${settingsRow.id}"""
        else
          settingsRow.text
        updateMatchText(settingsRow.component, baseText, searchText)
      }
    }
    for (groupPanel in groupPanels) {
      if (groupPanel !in visibleGroupPanels) {
        groupPanel.isVisible = false
      }
    }
    nothingFoundRow.visible = visibleGroupPanels.isEmpty()
    SettingsCounterUsagesCollector.ADVANDED_SETTINGS_SEARCH.log(matchCount, searchText?.length ?: 0, onlyShowModified)
  }

  private fun updateMatchText(component: JComponent, @NlsSafe baseText: String, @NlsSafe searchText: String?) {
    val text = searchText?.takeIf { it.isNotBlank() }?.let {
      @NlsSafe val highlightedText = SearchUtil.markup(baseText, it, UIUtil.getLabelFontColor(UIUtil.FontColor.NORMAL),
                                              UIUtil.getSearchMatchGradientStartColor())
      "<html>$highlightedText"
    } ?: baseText
    when (component) {
      is JLabel -> component.text = text
      is AbstractButton -> component.text = text
    }
  }

  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun enableSearch(option: String?): Runnable {
    return Runnable { applyFilter(option, false) }
  }
}
