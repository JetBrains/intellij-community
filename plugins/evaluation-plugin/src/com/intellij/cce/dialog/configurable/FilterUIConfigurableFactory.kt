package com.intellij.cce.dialog.configurable

import com.intellij.cce.filter.ConfigurableBuilder
import com.intellij.cce.filter.EvaluationFilterManager
import com.intellij.cce.filter.impl.PackageRegexFilterConfiguration
import com.intellij.cce.filter.impl.StaticFilterConfiguration
import com.intellij.cce.filter.impl.TypeFilterConfiguration
import com.intellij.cce.workspace.Config
import com.intellij.ui.layout.*

class FilterUIConfigurableFactory(private val previousState: Config, private val layout: LayoutBuilder) :
  ConfigurableBuilder<Row> {
  override fun build(filterId: String): UIConfigurable {
    val previousFilterState = previousState.actions.strategy.filters[filterId]
                              ?: EvaluationFilterManager.getConfigurationById(filterId)?.defaultFilter()
                              ?: throw IllegalArgumentException("Unknown filter id: $filterId")
    return when (filterId) {
      TypeFilterConfiguration.id -> TypeUIConfigurable(previousFilterState, layout)
      StaticFilterConfiguration.id -> StaticUIConfigurable(previousFilterState, layout)
      PackageRegexFilterConfiguration.id -> PackageRegexUIConfigurable(previousFilterState, layout)
      else -> throw IllegalStateException("UI configuration is not supported for filter id = $filterId")
    }
  }
}