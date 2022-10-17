package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.workspace.Config
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import java.awt.event.ItemEvent
import javax.swing.*

class FlowConfigurable : EvaluationConfigurable {
  companion object {
    private const val statsCollectorId = "com.intellij.stats.completion"
  }

  private var saveLogs = true
  private lateinit var workspaceDirTextField: JTextField
  private lateinit var trainTestSpinner: JSpinner

  override fun createPanel(previousState: Config): JPanel {
    saveLogs = previousState.interpret.saveLogs
    val statsCollectorEnabled = PluginManagerCore.getPlugin(PluginId.getId(statsCollectorId))?.isEnabled ?: false
    trainTestSpinner = JSpinner(SpinnerNumberModel(previousState.interpret.trainTestSplit, 1, 99, 1)).apply {
      isEnabled = saveLogs && statsCollectorEnabled
    }

    return panel {
      group(EvaluationPluginBundle.message("evaluation.settings.flow.title")) {
        row(EvaluationPluginBundle.message("evaluation.settings.flow.workspace")) {
          workspaceDirTextField = textField()
            .text(previousState.outputDir)
            .align(AlignX.FILL)
            .component
        }
        row(EvaluationPluginBundle.message("evaluation.settings.flow.logs.save")) {
          checkBox("")
            .applyToComponent { isSelected = saveLogs }
            .configureSaveLogs(statsCollectorEnabled)
        }
        row(EvaluationPluginBundle.message("evaluation.settings.flow.logs.split")) {
          cell(trainTestSpinner)
        }
      }
    }
  }

  override fun configure(builder: Config.Builder) {
    builder.outputDir = workspaceDirTextField.text
    builder.saveLogs = saveLogs
    builder.trainTestSplit = trainTestSpinner.value as Int
  }

  private fun Cell<JCheckBox>.configureSaveLogs(statsCollectorEnabled: Boolean) {
    if (!statsCollectorEnabled) {
      saveLogs = false
      component.isSelected = false
      component.isEnabled = false
    }
    component.addItemListener { event ->
      saveLogs = event.stateChange == ItemEvent.SELECTED
      trainTestSpinner.isEnabled = saveLogs
    }
  }
}