// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

private class SettingsGroup(
  @JvmField val groupRow: Row,
  @JvmField val title: JBLabel,
  @JvmField val text: String,
  @JvmField val settingsRows: Collection<SettingsRow>,
)

private class SettingsRow(
  @JvmField val row: Row,
  @JvmField val component: JComponent,
  @JvmField val id: String,
  @JvmField val text: String,
  @JvmField val isDefaultPredicate: ComponentPredicate,
) {
  fun setVisible(visible: Boolean) {
    row.visible(visible)
  }
}

@ApiStatus.Internal
class AdvancedSettingsConfigurable : DslConfigurableBase(), SearchableConfigurable, Configurable.NoScroll {
  private val settingsGroups = mutableListOf<SettingsGroup>()
  private lateinit var nothingFoundRow: Row
  private var onlyShowModified = false

  private var searchAlarm = SingleAlarm(
    task = ::updateSearch,
    delay = 300,
    parentDisposable = null,
    threadToUse = Alarm.ThreadToUse.SWING_THREAD,
    modalityState = ModalityState.defaultModalityState(),
  )

  private val searchField = SearchTextField().apply {
    textEditor.emptyText.text = ApplicationBundle.message("search.advanced.settings")

    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAndRequest()
      }
    })
  }

  override fun createPanel(): DialogPanel {
    val extensionSettings = createExtensionSettings()
    val result = panel {
      row {
        cell(searchField)
          .align(AlignX.FILL)
          .resizableColumn()
        checkBox(ApplicationBundle.message("checkbox.advanced.settings.modified"))
          .actionListener { _, component ->
            onlyShowModified = component.isSelected
            updateSearch()
          }
      }.layout(RowLayout.INDEPENDENT)

      nothingFoundRow = row {
        label(ApplicationBundle.message("search.advanced.settings.nothing.found"))
          .align(Align.CENTER)
          .applyToComponent {
            foreground = NamedColorUtil.getInactiveTextColor()
          }
      }.visible(false)

      row {
        val scrollable = ScrollPaneFactory.createScrollPane(extensionSettings, true)
        scrollable.preferredSize = Dimension(JBUI.scale(300), JBUI.scale(200))
        cell(scrollable)
          .align(Align.FILL)
      }.resizableRow()
    }
    result.registerIntegratedPanel(extensionSettings)
    return result
  }

  private fun createExtensionSettings(): DialogPanel {
    val groupedExtensions = AdvancedSettingBean.EP_NAME.extensionList
      .filter { it.isApplicable() }
      .groupBy { it.group() ?: ApplicationBundle.message("group.advanced.settings.other") }
      .toSortedMap()

    return panel {
      for ((group, extensions) in groupedExtensions) {
        val settingsRows = mutableListOf<SettingsRow>()
        val title = JBLabel(group)
        val groupRow = group(title = title) {
          for (extension in extensions) {
            val label = if (extension.type() == AdvancedSettingType.Bool) null else JLabel(extension.title() + ":")
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
              val minSize = AllIcons.Diff.Revert.iconHeight + 4 // Add space for a border
              actionButton(resetAction)
                .applyToComponent {
                  setMinimumButtonSize(Dimension(minSize, minSize))
                  // Revert button is a little higher than checkbox, so disable default additional vertical gaps for the button
                  putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.NONE)
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

        settingsGroups.add(SettingsGroup(groupRow, title, group, settingsRows))
      }
    }
  }

  private fun AdvancedSettingBean.isApplicable(): Boolean {
    return when {
      id == "project.view.do.not.autoscroll.to.libraries" -> !ProjectJdkTable.getInstance().allJdks.isEmpty()
      else -> true
    }
  }

  private fun updateSearch() {
    applyFilter(searchField.text, onlyShowModified)
  }

  private fun resetFilter() {
    for (settingsGroup in settingsGroups) {
      settingsGroup.groupRow.visible(true)
      updateMatchText(settingsGroup.title, settingsGroup.text, null)
      for (settingsRow in settingsGroup.settingsRows) {
        settingsRow.setVisible(true)
        updateMatchText(settingsRow.component, settingsRow.text, null)
      }
    }
    nothingFoundRow.visible(false)
  }

  private fun applyFilter(searchText: String?, onlyShowModified: Boolean) {
    if (searchText.isNullOrBlank() && !onlyShowModified) {
      resetFilter()
      return
    }

    val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
    val filterWords = searchText?.let { searchableOptionsRegistrar.getProcessedWords(it) } ?: emptySet()
    val filterWordsRaw = searchText?.split(' ') ?: emptySet()
    var matchCount = 0

    for (settingsGroup in settingsGroups) {
      var groupVisible = false
      val groupNameMatched = isMatch(filterWords, settingsGroup.text)
      updateMatchText(settingsGroup.title, settingsGroup.text, searchText)
      if (!onlyShowModified && groupNameMatched) {
        matchCount++
        groupVisible = true
      }

      for (settingsRow in settingsGroup.settingsRows) {
        val idWords = settingsRow.id.split('.')
        val textMatches = searchText == null || isMatch(filterWords, settingsRow.text)
        val idMatches = searchText == null || (filterWordsRaw.isNotEmpty() && idWords.containsAll(filterWordsRaw))
        val modifiedMatches = if (onlyShowModified) !settingsRow.isDefaultPredicate() else true
        val matches = (groupNameMatched || textMatches || idMatches) && modifiedMatches
        settingsRow.setVisible(matches)
        if (matches) {
          matchCount++
          groupVisible = true
          val idColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
          val baseText = if (idMatches && !textMatches) {
            """${settingsRow.text}<br><pre><font color="${idColor}">${settingsRow.id}"""
          }
          else {
            settingsRow.text
          }
          updateMatchText(settingsRow.component, baseText, searchText)
        }
      }

      settingsGroup.groupRow.visible(groupVisible)
    }

    nothingFoundRow.visible(matchCount == 0)
    SettingsCounterUsagesCollector.ADVANDED_SETTINGS_SEARCH.log(matchCount, searchText?.length ?: 0, onlyShowModified)
  }

  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun getHelpTopic(): String = "Advanced_settings"

  override fun enableSearch(option: String?): Runnable {
    if (option != null && displayName.startsWith(option, ignoreCase = true)) {
      return Runnable { applyFilter("", false) }
    }
    return Runnable { applyFilter(option, false) }
  }
}

private fun isMatch(filterWords: Collection<String>, text: String): Boolean {
  val textWords = SearchableOptionsRegistrar.getInstance().getProcessedWords(text)
  return filterWords.all { textWords.contains(it) || text.contains(it, ignoreCase = true) }
}

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

private data class AdvancedSettingControl(
  @JvmField val cellBuilder: Cell<JComponent>,
  @JvmField val isDefault: ComponentPredicate,
  @JvmField val reset: () -> Unit,
)

@ApiStatus.Internal
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
