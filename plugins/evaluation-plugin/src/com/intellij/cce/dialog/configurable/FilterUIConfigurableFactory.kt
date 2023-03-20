package com.intellij.cce.dialog.configurable

import com.intellij.cce.filter.ConfigurableBuilder
import com.intellij.cce.filter.EvaluationFilterManager
import com.intellij.cce.filter.impl.PackageRegexFilterConfiguration
import com.intellij.cce.filter.impl.StaticFilterConfiguration
import com.intellij.cce.filter.impl.TypeFilterConfiguration
import com.intellij.cce.workspace.Config
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row

class FilterUIConfigurableFactory(private val previousState: Config, private val panel: Panel) :
  ConfigurableBuilder<Row> {
  override fun build(filterId: String): UIConfigurable {
    val previousFilterState = previousState.actions.strategy.filters[filterId]
                              ?: EvaluationFilterManager.getConfigurationById(filterId)?.defaultFilter()
                              ?: throw IllegalArgumentException("Unknown filter id: $filterId")
    return when (filterId) {
      TypeFilterConfiguration.id -> TypeUIConfigurable(previousFilterState, panel)
      StaticFilterConfiguration.id -> StaticUIConfigurable(previousFilterState, panel)
      PackageRegexFilterConfiguration.id -> PackageRegexUIConfigurable(previousFilterState, panel)
      else -> throw IllegalStateException("UI configuration is not supported for filter id = $filterId")
    }
  }
}