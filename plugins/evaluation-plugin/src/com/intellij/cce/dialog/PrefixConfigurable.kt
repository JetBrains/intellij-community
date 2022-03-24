package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.actions.CompletionPrefix
import com.intellij.cce.workspace.Config
import com.intellij.ui.layout.*
import java.awt.event.ItemEvent
import javax.swing.*

class PrefixConfigurable : EvaluationConfigurable {
  private var prefix: CompletionPrefix = CompletionPrefix.NoPrefix

  private val simplePrefixSpinner = JSpinner(SpinnerNumberModel(2, 1, 5, 1))
  private val emulateTypingCheckbox = JCheckBox("", prefix.emulateTyping)

  override fun createPanel(previousState: Config): JPanel {
    prefix = previousState.actions.strategy.prefix
    val panel = panel(title = EvaluationPluginBundle.message("evaluation.settings.prefix.title"), constraints = arrayOf(LCFlags.noGrid)) { }
    val elements = listOf(
      JRadioButton(EvaluationPluginBundle.message("evaluation.settings.prefix.no")).configureNoPrefix(),
      JRadioButton(EvaluationPluginBundle.message("evaluation.settings.prefix.simple")).configureSimplePrefix(),
      simplePrefixSpinner.configureSimplePrefix(),
      JRadioButton(EvaluationPluginBundle.message("evaluation.settings.prefix.capitalize")).configureCapitalizePrefix(),
      emulateTypingCheckbox.configureEmulateTyping(),
      JLabel(EvaluationPluginBundle.message("evaluation.settings.prefix.typing"))
    )
    val buttonGroup = ButtonGroup()
    elements.forEach {
      if (it is JRadioButton) buttonGroup.add(it)
      panel.add(it as JComponent)
    }
    return panel
  }

  override fun configure(builder: Config.Builder) {
    builder.prefixStrategy = prefix
  }

  private fun JCheckBox.configureEmulateTyping(): JCheckBox {
    isEnabled = prefix !is CompletionPrefix.NoPrefix
    isSelected = prefix.emulateTyping
    addItemListener { event ->
      prefix = when (prefix) {
        is CompletionPrefix.SimplePrefix -> CompletionPrefix.SimplePrefix(event.stateChange == ItemEvent.SELECTED,
                                                                          simplePrefixSpinner.value as Int)
        is CompletionPrefix.CapitalizePrefix -> CompletionPrefix.CapitalizePrefix(event.stateChange == ItemEvent.SELECTED)
        is CompletionPrefix.NoPrefix -> CompletionPrefix.NoPrefix
      }
    }
    return this
  }

  private fun JSpinner.configureSimplePrefix(): JSpinner {
    isEnabled = prefix is CompletionPrefix.SimplePrefix
    addChangeListener {
      prefix = CompletionPrefix.SimplePrefix(emulateTypingCheckbox.isSelected, simplePrefixSpinner.value as Int)
    }
    return this
  }

  private fun JRadioButton.configureNoPrefix(): JRadioButton {
    isSelected = prefix == CompletionPrefix.NoPrefix
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        prefix = CompletionPrefix.NoPrefix
        simplePrefixSpinner.isEnabled = false
        emulateTypingCheckbox.isSelected = false
        emulateTypingCheckbox.isEnabled = false
      }
    }
    return this
  }

  private fun JRadioButton.configureSimplePrefix(): JRadioButton {
    isSelected = prefix is CompletionPrefix.SimplePrefix
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        prefix = CompletionPrefix.SimplePrefix(emulateTypingCheckbox.isSelected, simplePrefixSpinner.value as Int)
        simplePrefixSpinner.isEnabled = true
        emulateTypingCheckbox.isEnabled = true
      }
    }
    return this
  }

  private fun JRadioButton.configureCapitalizePrefix(): JRadioButton {
    isSelected = prefix is CompletionPrefix.CapitalizePrefix
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        prefix = CompletionPrefix.CapitalizePrefix(emulateTypingCheckbox.isSelected)
        simplePrefixSpinner.isEnabled = false
        emulateTypingCheckbox.isEnabled = true
      }
    }
    return this
  }
}