// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.hints.InlayHintsSwitch
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.InspectionsLevel
import com.intellij.openapi.editor.markup.LanguageHighlightLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI

class InspectionsSettingContent(val analyzerGetter: () -> AnalyzerStatus, project: Project) {
  val panel: DialogPanel = panel {
    panel {
      row { label(DaemonBundle.message("iw.inspection.popup.title")) }
      indent {
        row(DaemonBundle.message("iw.inspection.popup.current.level")) {
          val lv = analyzerGetter().controller.getHighlightLevels().first()
          comboBox(analyzerGetter().controller.availableLevels).onChanged {
            if(it.selectedItem is InspectionsLevel) {
              analyzerGetter().controller.setHighLightLevel(LanguageHighlightLevel(lv.langID, it.selectedItem as InspectionsLevel))
            }
          }.applyToComponent {
            selectedItem = lv.level
          }
        }
        row {
          link(DaemonBundle.message("iw.inspection.popup.configure")) {
            analyzerGetter().controller.toggleProblemsView()
          }
        }
      }
    }

    buttonsGroup(DaemonBundle.message("iw.inspection.popup.show")) {
      row {
        checkBox(DaemonBundle.message("iw.inspection.popup.hints")).onChanged {
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