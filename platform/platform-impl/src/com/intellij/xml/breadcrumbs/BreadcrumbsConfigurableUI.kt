// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.options.colors.pages.GeneralColorsPage
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox

internal class BreadcrumbsConfigurableUI(configurables: List<BreadcrumbsConfigurable.BreadcrumbsProviderConfigurable>) {

  private lateinit var show: JCheckBox

  lateinit var panel: DialogPanel

  init {
    panel = panel {
      val settings = EditorSettingsExternalizable.getInstance()
      val map = mutableMapOf<String, JCheckBox>()

      for (configurable in configurables) {
        val id = configurable.id
        if (!map.containsKey(id)) {
          map[id] = configurable.createComponent()
        }
      }
      val sortedList = map.toList().sortedWith(Comparator { o1, o2 -> StringUtil.naturalCompare(o1.second.text, o2.second.text) })

      row {
        show = checkBox(ApplicationBundle.message("checkbox.show.breadcrumbs"))
          .bindSelected(settings::isBreadcrumbsShown, settings::setBreadcrumbsShown)
          .component
      }

      indent {
        buttonsGroup {
          row(ApplicationBundle.message("label.breadcrumbs.placement")) {
            radioButton(ApplicationBundle.message("radio.show.breadcrumbs.above"), true)
            radioButton(ApplicationBundle.message("radio.show.breadcrumbs.below"), false)
          }
        }.bind(settings::isBreadcrumbsAbove, settings::setBreadcrumbsAbove)

        row {
          label(ApplicationBundle.message("label.breadcrumbs.languages")).applyToComponent {
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(bottom = false))
          }
        }

        panel {
          for (i in sortedList.indices step 3) {
            row {
              for (j in 0..2) {
                sortedList.getOrNull(i + j)?.let { (id, checkBox) ->
                  cell(checkBox)
                    .bindSelected({ settings.isBreadcrumbsShownFor(id) }, { settings.setBreadcrumbsShownFor(id, it) })
                    .gap(RightGap.COLUMNS)
                }
              }
            }.layout(RowLayout.PARENT_GRID)
          }
        }
      }.enabledIf(show.selected)

      row {
        link(ApplicationBundle.message("configure.breadcrumbs.colors")) {
          val context = DataManager.getInstance().getDataContext(panel)
          ColorAndFontOptions.selectOrEditColor(context, "Breadcrumbs//Current", GeneralColorsPage::class.java)
        }
      }.topGap(TopGap.SMALL)
    }
  }
}
