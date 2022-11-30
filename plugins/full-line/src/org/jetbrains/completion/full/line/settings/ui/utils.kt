package org.jetbrains.completion.full.line.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.AbstractButton
import kotlin.reflect.KMutableProperty0

const val AUTH_TOKEN_UPDATE = "full-line@button-changed"
const val LANGUAGE_CHECKBOX_NAME = "full-line@language-checkbox"

// Required for compatibility with the latest IDEs
fun RowBuilder.fullRow(init: InnerCell.() -> Unit) = row { cell(isFullWidth = true, init = init) }

fun RowBuilder.extended(block: RowBuilder.() -> Unit) {
  if (MLServerCompletionSettings.isExtended()) this.apply(block) else return
}

fun Panel.extended(block: Panel.() -> Unit) {
  if (MLServerCompletionSettings.isExtended()) this.apply(block) else return
}

@Deprecated("Use Kotlin UI DSL Version 2")
fun Row.enableSubRowsIf(predicate: ComponentPredicate) {
  subRowsEnabled = predicate()
  predicate.addListener { subRowsEnabled = it }
}

val AbstractButton.visible: ComponentPredicate
  get() = object : ComponentPredicate() {
    override fun invoke(): Boolean = isVisible

    override fun addListener(listener: (Boolean) -> Unit) {
      addChangeListener { listener(isVisible) }
    }
  }


fun CellBuilder<ComboBox<ModelType>>.withModelTypeBinding(modelProperty: KMutableProperty0<ModelType>): CellBuilder<ComboBox<ModelType>> {
  return withBinding(
    { component ->
      if (component.selectedItem != null) {
        component.selectedItem as ModelType
      }
      else {
        MLServerCompletionSettings.getInstance().getModelMode()
      }
    },
    { component, value -> component.setSelectedItem(value) },
    modelProperty.toBinding()
  )
}

fun DialogPanel.copyCallbacksFromChild(child: DialogPanel): DialogPanel {
  validateCallbacks = validateCallbacks + child.validateCallbacks
  componentValidateCallbacks = componentValidateCallbacks + child.componentValidateCallbacks

  customValidationRequestors = customValidationRequestors + child.customValidationRequestors
  applyCallbacks = applyCallbacks + child.applyCallbacks
  resetCallbacks = resetCallbacks + child.resetCallbacks
  isModifiedCallbacks = isModifiedCallbacks + child.isModifiedCallbacks
  return this
}

fun DialogPanel.clearCallbacks(): DialogPanel {
  validateCallbacks = emptyList()
  componentValidateCallbacks = emptyMap()

  customValidationRequestors = emptyMap()
  applyCallbacks = emptyMap()
  resetCallbacks = emptyMap()
  isModifiedCallbacks = emptyMap()
  return this
}

fun languageConfigurationKey(language: String, type: ModelType) = "${language}-${type.name}"

fun connectLanguageWithModelType(modelTypeComboBox: ComboBox<ModelType>, languageComboBox: ComboBox<String>, configPanel: DialogPanel) {
  ItemListener {
    if (it.stateChange == ItemEvent.SELECTED) {
      val key = languageConfigurationKey(languageComboBox.selectedItem as String, modelTypeComboBox.selectedItem as ModelType)
      (configPanel.layout as JBCardLayout).show(configPanel, key)
    }
  }.let {
    modelTypeComboBox.addItemListener(it)
    languageComboBox.addItemListener(it)
  }
}

fun DialogPanel.languageCheckboxes() = components.filterIsInstance<JBCheckBox>().filter { it.name == LANGUAGE_CHECKBOX_NAME }

fun JBCheckBox.connectWith(other: JBCheckBox) = addItemListener {
  other.isSelected = isSelected
}
