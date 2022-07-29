package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.workspace.Config
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.*
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class FilteringOnInterpretationConfigurable : EvaluationConfigurable {
  private lateinit var probabilityTextField: JTextField
  private lateinit var seedTextField: JTextField

  override fun createPanel(previousState: Config): JPanel {
    probabilityTextField = JTextField(previousState.interpret.completeTokenProbability.toString())
    seedTextField = JTextField(previousState.interpret.completeTokenSeed?.toString() ?: "").apply {
      isEnabled = 0 < previousState.interpret.completeTokenProbability &&
                  previousState.interpret.completeTokenProbability < 1
    }

    return panel(title = EvaluationPluginBundle.message("evaluation.settings.interpretation.title")) {
      row {
        cell {
          label(EvaluationPluginBundle.message("evaluation.settings.interpretation.probability"))
          probabilityTextField(growPolicy = GrowPolicy.SHORT_TEXT).apply {
            component.document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                val value = probabilityTextField.text.toDoubleOrNull()
                seedTextField.isEnabled = value != null && 0 < value && value < 1
              }
            })
          }
        }
      }
      row {
        cell {
          label(EvaluationPluginBundle.message("evaluation.settings.interpretation.seed"))
          seedTextField(growPolicy = GrowPolicy.SHORT_TEXT)
        }
      }
    }
  }

  override fun configure(builder: Config.Builder) {
    builder.completeTokenProbability = probabilityTextField.text.toDoubleOrNull() ?: 1.0
    builder.completeTokenSeed = seedTextField.text.toLongOrNull()
  }
}