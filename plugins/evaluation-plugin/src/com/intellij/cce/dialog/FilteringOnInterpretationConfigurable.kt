package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.workspace.Config
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class FilteringOnInterpretationConfigurable : EvaluationConfigurable {
  private lateinit var probabilityTextField: JTextField
  private lateinit var seedTextField: JTextField

  override fun createPanel(previousState: Config): JPanel {
    return panel {
      group(EvaluationPluginBundle.message("evaluation.settings.interpretation.title")) {
        row(EvaluationPluginBundle.message("evaluation.settings.interpretation.probability")) {
          probabilityTextField = textField().applyToComponent {
            text = previousState.interpret.completeTokenProbability.toString()
            document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                val value = probabilityTextField.text.toDoubleOrNull()
                seedTextField.isEnabled = value != null && 0 < value && value < 1
              }
            })
          }.component
        }
        row(EvaluationPluginBundle.message("evaluation.settings.interpretation.seed")) {
          seedTextField = textField()
            .applyToComponent {
              text = previousState.interpret.completeTokenSeed?.toString() ?: ""
              isEnabled = 0 < previousState.interpret.completeTokenProbability &&
                          previousState.interpret.completeTokenProbability < 1
            }.component
        }
      }
    }
  }

  override fun configure(builder: Config.Builder) {
    builder.completeTokenProbability = probabilityTextField.text.toDoubleOrNull() ?: 1.0
    builder.completeTokenSeed = seedTextField.text.toLongOrNull()
  }
}