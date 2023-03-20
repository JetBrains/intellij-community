package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.actions.CompletionType
import com.intellij.cce.workspace.Config
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ItemEvent
import javax.swing.JPanel

class CompletionTypeConfigurable : EvaluationConfigurable {
  private var completionType: CompletionType = CompletionType.BASIC

  override fun createPanel(previousState: Config): JPanel {
    completionType = previousState.interpret.completionType
    return panel {
      group(EvaluationPluginBundle.message("evaluation.settings.type.title")) {
        buttonsGroup {
          row {
            radioButton(EvaluationPluginBundle.message("evaluation.settings.type.basic")).configure(CompletionType.BASIC)
            radioButton(EvaluationPluginBundle.message("evaluation.settings.type.smart")).configure(CompletionType.SMART)
            radioButton(EvaluationPluginBundle.message("evaluation.settings.type.ml")).configure(CompletionType.ML)
            radioButton(EvaluationPluginBundle.message("evaluation.settings.type.full.line")).configure(CompletionType.FULL_LINE)
              .enabled(isFullLineEnabled())
          }
        }
      }
    }
  }

  override fun configure(builder: Config.Builder) {
    builder.completionType = completionType
    builder.evaluationTitle = completionType.name
  }

  private fun isFullLineEnabled(): Boolean {
    return PluginManager.getInstance()
             .findEnabledPlugin(PluginId.getId("org.jetbrains.completion.full.line"))?.isEnabled ?: false
  }

  private fun Cell<JBRadioButton>.configure(value: CompletionType): Cell<JBRadioButton> {
    return this.apply {
      component.isSelected = completionType == value
      component.addItemListener { event ->
        if (event.stateChange == ItemEvent.SELECTED) completionType = value
      }
    }
  }
}
