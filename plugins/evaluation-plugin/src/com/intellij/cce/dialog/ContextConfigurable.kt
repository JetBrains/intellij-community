package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.actions.CompletionContext
import com.intellij.cce.workspace.Config
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import java.awt.event.ItemEvent
import javax.swing.JPanel

class ContextConfigurable : EvaluationConfigurable {
  private var context: CompletionContext = CompletionContext.ALL

  override fun createPanel(previousState: Config): JPanel {
    context = previousState.actions.strategy.context
    return panel(title = EvaluationPluginBundle.message("evaluation.settings.context.title"), constraints = arrayOf(LCFlags.noGrid)) {
      buttonGroup {
        row {
          radioButton(EvaluationPluginBundle.message("evaluation.settings.context.all")).configure(CompletionContext.ALL)
          radioButton(EvaluationPluginBundle.message("evaluation.settings.context.previous")).configure(CompletionContext.PREVIOUS)
        }
      }
    }
  }

  override fun configure(builder: Config.Builder) {
    builder.contextStrategy = context
  }

  private fun CellBuilder<JBRadioButton>.configure(value: CompletionContext) {
    component.isSelected = context == value
    component.addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) context = value
    }
  }
}
