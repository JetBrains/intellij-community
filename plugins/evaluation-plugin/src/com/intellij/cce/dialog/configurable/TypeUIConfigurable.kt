package com.intellij.cce.dialog.configurable

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.impl.TypeFilter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import java.awt.event.ItemEvent
import javax.swing.JCheckBox

class TypeUIConfigurable(previousState: EvaluationFilter, private val panel: Panel) : UIConfigurable {
  private val types: MutableList<TypeProperty> =
    if (previousState == EvaluationFilter.ACCEPT_ALL) mutableListOf()
    else (previousState as TypeFilter).values.toMutableList()

  private val allButton = JBCheckBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.all"))
  private val typeButtons = mutableListOf<JCheckBox>()

  override val view: Row = createView()

  override fun build(): EvaluationFilter {
    return if (types.isEmpty()) EvaluationFilter.ACCEPT_ALL else TypeFilter(types)
  }

  private fun createView(): Row {
    return panel.row(EvaluationPluginBundle.message("evaluation.settings.filters.type.title")) {
      cell(allButton)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.keywords")).configure(TypeProperty.KEYWORD)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.methods")).configure(TypeProperty.METHOD_CALL)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.fields")).configure(TypeProperty.FIELD)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.variables")).configure(TypeProperty.VARIABLE)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.types")).configure(TypeProperty.TYPE_REFERENCE)
      checkBox(EvaluationPluginBundle.message("evaluation.settings.filters.type.arguments")).configure(TypeProperty.ARGUMENT_NAME)
      allButton.configure()
    }
  }

  private fun JBCheckBox.configure() {
    isSelected = types.isEmpty()
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        types.clear()
        typeButtons.forEach { it.isSelected = false }
      }
      else if (types.isEmpty()) {
        isSelected = true
      }
    }
  }

  private fun Cell<JCheckBox>.configure(value: TypeProperty) {
    typeButtons.add(component)
    component.isSelected = types.contains(value)
    component.addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        if (!types.contains(value)) {
          types.add(value)
          allButton.isSelected = false
        }
      } else {
        types.remove(value)
        if (types.isEmpty()) {
          allButton.isSelected = true
        }
      }
    }
  }
}