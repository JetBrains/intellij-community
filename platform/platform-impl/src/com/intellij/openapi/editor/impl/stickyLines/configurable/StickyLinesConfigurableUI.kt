// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.configurable

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.options.colors.pages.GeneralColorsPage
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.asRange
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox

internal class StickyLinesConfigurableUI(configurables: List<StickyLinesProviderConfigurable>) {

  private lateinit var showCheckbox: JCheckBox
  lateinit var panel: DialogPanel

  init {
    panel = panel {
      val settings = EditorSettingsExternalizable.getInstance()
      val languages = languageCheckboxList(configurables)
      row {
        showCheckbox = checkBox(ApplicationBundle.message("checkbox.show.sticky.lines"))
          .bindSelected(settings::areStickyLinesShown, settings::setStickyLinesShown)
          .gap(RightGap.SMALL)
          .component
      }
      indent {
        row(label = ApplicationBundle.message("label.show.sticky.lines")) {
          intTextField(UINumericRange(5, 1, 20).asRange())
            .bindIntText(settings::getStickyLineLimit, settings::setStickyLineLimit)
            .columns(2)
        }
        if (languages.isNotEmpty()) {
          row {
            label(ApplicationBundle.message("label.sticky.lines.languages"))
          }
          panel {
            val rowCount = (languages.size + 2) / 3
            for (i in 0..<rowCount) {
              row {
                for (j in 0..2) {
                  languages.getOrNull(i + rowCount * j)?.let { (langId, checkBox) ->
                    cell(checkBox)
                      .bindSelected(
                        { settings.areStickyLinesShownFor(langId) },
                        { settings.setStickyLinesShownFor(langId, it) }
                      ).gap(RightGap.COLUMNS)
                  }
                }
              }.layout(RowLayout.PARENT_GRID)
            }
          }
        }
      }.enabledIf(showCheckbox.selected)

      row {
        link(ApplicationBundle.message("configure.sticky.lines.colors")) {
          val context = DataManager.getInstance().getDataContext(panel)
          ColorAndFontOptions.selectOrEditColor(context, "Sticky Lines//Background", GeneralColorsPage::class.java)
        }
      }.topGap(TopGap.SMALL)
    }
  }

  private fun languageCheckboxList(configurables: List<StickyLinesProviderConfigurable>): List<Pair<String, JCheckBox>> {
    val map = mutableMapOf<String, JCheckBox>()
    for (configurable in configurables) {
      val languageId = configurable.id
      if (!map.containsKey(languageId)) {
        map[languageId] = configurable.createComponent()
      }
    }
    return map.toList().sortedWith(Comparator { o1, o2 ->
        StringUtil.naturalCompare(o1.second.text, o2.second.text)
      })
  }
}
