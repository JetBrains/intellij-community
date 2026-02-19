// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.hints.InlayHintsSwitch
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.InspectionsFUS
import com.intellij.openapi.editor.markup.InspectionsLevel
import com.intellij.openapi.editor.markup.LanguageHighlightLevel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI

class InspectionsSettingContent(val analyzerGetter: () -> AnalyzerStatus, project: Project, fusTabId: Int) {
  private var link: ActionLink? = null
  val panel: DialogPanel = panel {
    panel {
      row { label(DaemonBundle.message("iw.inspection.popup.title")) }
      indent {
        row(DaemonBundle.message("iw.inspection.popup.current.level")) {
          val lv = analyzerGetter().controller.getHighlightLevels().first()
          comboBox(analyzerGetter().controller.availableLevels).onChanged {
            if(it.selectedItem is InspectionsLevel) {
              val level = it.selectedItem as InspectionsLevel
              val controller = analyzerGetter().controller
              if(analyzerGetter().controller.getHighlightLevels().first().level != level) {
                InspectionsFUS.currentFileLevelChanged(project, fusTabId, level)
              }
              controller.setHighLightLevel(LanguageHighlightLevel(lv.langID, level))
            }
          }.applyToComponent {
            selectedItem = lv.level
          }
        }
        row {
          link = link(DaemonBundle.message("iw.inspection.popup.configure")) { event ->
            InspectionsFUS.signal(project, fusTabId, InspectionsFUS.InspectionsEvent.TOGGLE_PROBLEMS_VIEW)
            link?.let {
              val ev = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, null, DataManager.getInstance().getDataContext(it))

              ActionManager.getInstance().getAction("ConfigureInspectionsAction")?.actionPerformed(ev) ?: run {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, InspectionsBundle.message("inspection.root.node.title"))
              }
            }
          }.component
        }
      }
    }

    buttonsGroup(DaemonBundle.message("iw.inspection.popup.show")) {
      row {
        checkBox(DaemonBundle.message("iw.inspection.popup.hints")).onChanged {
          if(InlayHintsSwitch.isEnabled(project) != it.isSelected) {
            InspectionsFUS.setInlayHintsEnabled(project, fusTabId, it.isSelected)
          }
          InlayHintsSwitch.setEnabled(project, it.isSelected)
        }.applyToComponent {
          isSelected = InlayHintsSwitch.isEnabled(project)
        }
      }
/*      row {
        checkBox("Code Vision")
      }*/
    }

  }.apply {
    border = JBUI.Borders.empty(10, 15)
  }
}