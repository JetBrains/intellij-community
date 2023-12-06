// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntryTable
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.table.AbstractTableModel

class ImportSettingsPanelWrapper(settings: CodeStyleSettings) : CodeStyleAbstractPanel(KotlinLanguage.INSTANCE, null, settings) {

  private val importsPanel = ImportSettingsPanel()
  private val content = JBScrollPane(importsPanel.panel)

  override fun getRightMargin() = throw UnsupportedOperationException()

  override fun createHighlighter(scheme: EditorColorsScheme) = throw UnsupportedOperationException()

  override fun getFileType() = throw UnsupportedOperationException()

  override fun getPreviewText(): String? = null

  override fun apply(settings: CodeStyleSettings) = importsPanel.apply(settings.kotlinCustomSettings)

  override fun isModified(settings: CodeStyleSettings) = importsPanel.isModified(settings.kotlinCustomSettings)

  override fun getPanel() = content

  override fun resetImpl(settings: CodeStyleSettings) {
    importsPanel.reset(settings.kotlinCustomSettings)
  }

  override fun getTabTitle() = ApplicationBundle.message("title.imports")
}

private class ImportSettingsPanel {
  private lateinit var cbImportNestedClasses: JCheckBox

  private val starImportLayoutPanel = KotlinStarImportLayoutPanel()
  private val importOrderLayoutPanel = KotlinImportOrderLayoutPanel()

  private val nameCountToUseStarImportSelector = NameCountToUseStarImportSelector(
    KotlinBundle.message("formatter.title.top.level.symbols"),
    KotlinCodeStyleSettings.DEFAULT_NAME_COUNT_TO_USE_STAR_IMPORT,
  )

  private val nameCountToUseStarImportForMembersSelector = NameCountToUseStarImportSelector(
    KotlinBundle.message("formatter.title.java.statics.and.enum.members"),
    KotlinCodeStyleSettings.DEFAULT_NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS,
  )

  val panel = panel {
    with(nameCountToUseStarImportSelector) {
      buildUi()
    }
    with(nameCountToUseStarImportForMembersSelector) {
      buildUi()
    }

    group(KotlinBundle.message("formatter.title.other")) {
      row {
        cbImportNestedClasses = checkBox(KotlinBundle.message("formatter.checkbox.text.insert.imports.for.nested.classes"))
          .component
      }
    }

    row {
      cell(starImportLayoutPanel).align(AlignX.FILL)
    }.bottomGap(BottomGap.MEDIUM)

    row {
      cell(importOrderLayoutPanel).align(AlignX.FILL)
    }
  }.apply {
    border = JBUI.Borders.empty(0, 10, 10, 10)
  }

  fun reset(settings: KotlinCodeStyleSettings) {
    nameCountToUseStarImportSelector.value = settings.NAME_COUNT_TO_USE_STAR_IMPORT
    nameCountToUseStarImportForMembersSelector.value = settings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS

    cbImportNestedClasses.isSelected = settings.IMPORT_NESTED_CLASSES

    starImportLayoutPanel.packageTable.copyFrom(settings.PACKAGES_TO_USE_STAR_IMPORTS)
    (starImportLayoutPanel.layoutTable.model as AbstractTableModel).fireTableDataChanged()
    if (starImportLayoutPanel.layoutTable.rowCount > 0) {
      starImportLayoutPanel.layoutTable.selectionModel.setSelectionInterval(0, 0)
    }

    importOrderLayoutPanel.packageTable.copyFrom(settings.PACKAGES_IMPORT_LAYOUT)
    (importOrderLayoutPanel.layoutTable.model as AbstractTableModel).fireTableDataChanged()
    if (importOrderLayoutPanel.layoutTable.rowCount > 0) {
      importOrderLayoutPanel.layoutTable.selectionModel.setSelectionInterval(0, 0)
    }

    importOrderLayoutPanel.recomputeAliasesCheckbox()
  }

  fun apply(settings: KotlinCodeStyleSettings) {
    settings.NAME_COUNT_TO_USE_STAR_IMPORT = nameCountToUseStarImportSelector.value
    settings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS = nameCountToUseStarImportForMembersSelector.value
    settings.IMPORT_NESTED_CLASSES = cbImportNestedClasses.isSelected
    settings.PACKAGES_TO_USE_STAR_IMPORTS.copyFrom(getCopyWithoutEmptyPackages(starImportLayoutPanel.packageTable))
    settings.PACKAGES_IMPORT_LAYOUT.copyFrom(importOrderLayoutPanel.packageTable)
  }

  fun isModified(settings: KotlinCodeStyleSettings): Boolean {
    return with(settings) {
      var isModified = false
      isModified = isModified || nameCountToUseStarImportSelector.value != NAME_COUNT_TO_USE_STAR_IMPORT
      isModified = isModified || nameCountToUseStarImportForMembersSelector.value != NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS
      isModified = isModified || isModified(cbImportNestedClasses, IMPORT_NESTED_CLASSES)
      isModified = isModified ||
                   isModified(getCopyWithoutEmptyPackages(starImportLayoutPanel.packageTable), PACKAGES_TO_USE_STAR_IMPORTS)

      isModified = isModified || isModified(importOrderLayoutPanel.packageTable, PACKAGES_IMPORT_LAYOUT)

      isModified
    }
  }

  companion object {
    private fun isModified(checkBox: JCheckBox, value: Boolean): Boolean {
      return checkBox.isSelected != value
    }

    private fun isModified(list: KotlinPackageEntryTable, table: KotlinPackageEntryTable): Boolean {
      if (list.entryCount != table.entryCount) {
        return true
      }

      for (i in 0 until list.entryCount) {
        val entry1 = list.getEntryAt(i)
        val entry2 = table.getEntryAt(i)
        if (entry1 != entry2) {
          return true
        }
      }

      return false
    }

    private fun getCopyWithoutEmptyPackages(table: KotlinPackageEntryTable): KotlinPackageEntryTable {
      try {
        val copy = table.clone()
        copy.removeEmptyPackages()
        return copy
      }
      catch (ignored: CloneNotSupportedException) {
        throw IllegalStateException("Clone should be supported")
      }
    }
  }

  class NameCountToUseStarImportSelector(private val title: @NlsContexts.BorderTitle String, private val default: Int) {
    private lateinit var rbUseSingleImports: JRadioButton
    private lateinit var rbUseStarImports: JRadioButton
    private lateinit var rbUseStarImportsIfAtLeast: JRadioButton
    private lateinit var starImportLimitField: JBIntSpinner

    fun Panel.buildUi() {
      buttonsGroup(title) {
        row {
          rbUseSingleImports = radioButton(KotlinBundle.message("formatter.button.text.use.single.name.import"))
            .component
        }
        row {
          rbUseStarImports = radioButton(KotlinBundle.message("formatter.button.text.use.import.with"))
            .component
        }
        row {
          rbUseStarImportsIfAtLeast = radioButton(KotlinBundle.message("formatter.button.text.use.import.with.when.at.least"))
            .gap(RightGap.SMALL)
            .component
          starImportLimitField = spinner(MIN_VALUE..MAX_VALUE)
            .applyToComponent { number = default }
            .gap(RightGap.SMALL)
            .enabledIf(rbUseStarImportsIfAtLeast.selected)
            .component
          label(KotlinBundle.message("formatter.text.names.used"))
        }
      }
    }

    var value: Int
      get() {
        return when {
          rbUseSingleImports.isSelected -> Int.MAX_VALUE
          rbUseStarImports.isSelected -> 1
          else -> starImportLimitField.number
        }
      }
      set(value) {
        when {
          value > MAX_VALUE -> rbUseSingleImports.isSelected = true

          value < MIN_VALUE -> rbUseStarImports.isSelected = true

          else -> {
            rbUseStarImportsIfAtLeast.isSelected = true
            starImportLimitField.number = value
          }
        }
      }

    companion object {
      private const val MIN_VALUE = 2
      private const val MAX_VALUE = 100
    }
  }
}
