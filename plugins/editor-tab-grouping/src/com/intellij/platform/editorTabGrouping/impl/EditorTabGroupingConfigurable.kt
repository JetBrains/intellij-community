// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.application.options.editor.EditorTabsOptionsCustomSection
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.IslandsState
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class EditorTabGroupingConfigurable : EditorTabsOptionsCustomSection {
  private var panel: DialogPanel? = null

  override fun createComponent(): JComponent {
    val settings = EditorTabGroupingSettings.getInstance()
    val islandsEnabled = if (IslandsState.isEnabled()) ComponentPredicate.TRUE else ComponentPredicate.FALSE
    return panel {
      group(EditorTabGroupingBundle.message("settings.group.title")) {
        lateinit var groupByDirectory: JCheckBox
        row {
          groupByDirectory = checkBox(EditorTabGroupingBundle.message("settings.group.by.directory"))
            .bindSelected(settings::groupByDirectory)
            .component
        }
        row {
          checkBox(EditorTabGroupingBundle.message("settings.show.group.names"))
            .bindSelected(settings::showGroupNames)
            .enabledIf(groupByDirectory.selected and islandsEnabled)
        }
        row {
          checkBox(EditorTabGroupingBundle.message("settings.collapse.groups"))
            .bindSelected(settings::collapseGroups)
            .comment(EditorTabGroupingBundle.message("settings.collapse.groups.description"))
            .enabledIf(groupByDirectory.selected)
        }
        buttonsGroup(EditorTabGroupingBundle.message("settings.group.label.style")) {
          row {
            radioButton(EditorTabGroupingBundle.message("settings.group.label.style.outline"), GroupLabelStyle.OUTLINE)
          }
          row {
            radioButton(EditorTabGroupingBundle.message("settings.group.label.style.rail"), GroupLabelStyle.RAIL)
          }
        }.bind(settings::groupLabelStyle)
          .enabledIf(groupByDirectory.selected and islandsEnabled)
      }
    }.also { panel = it }
  }

  override fun isModified(): Boolean = panel?.isModified() == true

  override fun apply() {
    panel?.apply()
    UISettings.getInstance().fireUISettingsChanged()
  }

  override fun reset() {
    panel?.reset()
  }

  override fun disposeUIResources() {
    panel = null
  }
}
