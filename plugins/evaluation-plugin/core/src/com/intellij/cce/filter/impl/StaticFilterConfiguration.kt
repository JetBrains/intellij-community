package com.intellij.cce.filter.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.Language
import com.intellij.cce.core.PropertyAdapters
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class StaticFilter(val expectedValue: Boolean) : EvaluationFilter {
  override fun shouldEvaluate(properties: TokenProperties): Boolean {
    return PropertyAdapters.Jvm.adapt(properties)?.isStatic == expectedValue
  }

  override fun toJson(): JsonElement = JsonPrimitive(expectedValue)
}

class StaticFilterConfiguration : EvaluationFilterConfiguration {
  companion object {
    const val id = "isStatic"
  }

  override val id: String = StaticFilterConfiguration.id
  override val description: String = "Filter out token if it's static member access"
  override val hasUI: Boolean = true

  override fun isLanguageSupported(languageName: String): Boolean = listOf(Language.JAVA,
                                                                           Language.KOTLIN).any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter = if (json == null) EvaluationFilter.ACCEPT_ALL
  else StaticFilter(json as Boolean)

  override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
}