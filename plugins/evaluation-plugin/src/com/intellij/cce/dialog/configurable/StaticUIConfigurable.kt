package com.intellij.cce.dialog.configurable

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import java.awt.event.ItemEvent

class StaticUIConfigurable(previousState: EvaluationFilter, private val layout: LayoutBuilder) : UIConfigurable {
  private enum class StaticFilter {
    ALL,
    STATIC,
    NOT_STATIC
  }

  private var staticType =
    if (previousState is com.intellij.cce.filter.impl.StaticFilter) {
      if (previousState.expectedValue) StaticFilter.STATIC else StaticFilter.NOT_STATIC
    }
    else StaticFilter.ALL

  override val view: Row = createView()

  override fun build(): EvaluationFilter {
    return when (staticType) {
      StaticFilter.ALL -> EvaluationFilter.ACCEPT_ALL
      StaticFilter.NOT_STATIC -> com.intellij.cce.filter.impl.StaticFilter(false)
      StaticFilter.STATIC -> com.intellij.cce.filter.impl.StaticFilter(true)
    }
  }

  private fun createView(): Row = layout.row {
    buttonGroup {
      cell {
        label(EvaluationPluginBundle.message("evaluation.settings.filters.static.title"))
        radioButton(EvaluationPluginBundle.message("evaluation.settings.filters.static.all")).configure(StaticFilter.ALL)
        radioButton(EvaluationPluginBundle.message("evaluation.settings.filters.static.yes")).configure(StaticFilter.STATIC)
        radioButton(EvaluationPluginBundle.message("evaluation.settings.filters.static.no")).configure(StaticFilter.NOT_STATIC)
      }
    }
  }

  private fun CellBuilder<JBRadioButton>.configure(value: StaticFilter) {
    component.isSelected = staticType == value
    component.addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) staticType = value
    }
  }
}