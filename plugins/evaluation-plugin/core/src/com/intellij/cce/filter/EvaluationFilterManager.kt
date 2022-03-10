package com.intellij.cce.filter

import com.intellij.cce.filter.impl.FeaturesFilterConfiguration
import com.intellij.cce.filter.impl.PackageRegexFilterConfiguration
import com.intellij.cce.filter.impl.StaticFilterConfiguration
import com.intellij.cce.filter.impl.TypeFilterConfiguration

object EvaluationFilterManager {
  private val id2Configuration: MutableMap<String, EvaluationFilterConfiguration> = mutableMapOf()

  init {
    register(TypeFilterConfiguration())
    register(StaticFilterConfiguration())
    register(PackageRegexFilterConfiguration())
    register(FeaturesFilterConfiguration())
  }

  fun getConfigurationById(id: String): EvaluationFilterConfiguration? = id2Configuration[id]

  fun getAllFilters(): List<EvaluationFilterConfiguration> = id2Configuration.values.toList()

  fun registerFilter(configuration: EvaluationFilterConfiguration) {
    register(configuration)
  }

  fun unregisterFilter(configuration: EvaluationFilterConfiguration) {
    id2Configuration.remove(configuration.id)
  }

  private fun register(configuration: EvaluationFilterConfiguration) {
    val old = id2Configuration[configuration.id]
    if (old != null) {
      System.err.println("Configuration with id [${old.id}] already created. " +
                         "Classes: ${old.javaClass.canonicalName}, ${configuration.javaClass.canonicalName}")
      return
    }

    id2Configuration[configuration.id] = configuration
  }
}