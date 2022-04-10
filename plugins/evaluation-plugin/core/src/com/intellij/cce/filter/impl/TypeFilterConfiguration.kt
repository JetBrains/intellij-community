package com.intellij.cce.filter.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class TypeFilter(val values: List<TypeProperty>) : EvaluationFilter {
  override fun shouldEvaluate(properties: TokenProperties): Boolean = values.contains(properties.tokenType)
  override fun toJson(): JsonElement {
    val json = JsonArray()
    for (value in values)
      json.add(JsonPrimitive(value.name))
    return json
  }
}

class TypeFilterConfiguration : EvaluationFilterConfiguration {
  companion object {
    const val id = "statementTypes"
  }

  override val id: String = TypeFilterConfiguration.id
  override val description: String = "Filter out tokens by statement type"
  override val hasUI: Boolean = true

  override fun isLanguageSupported(languageName: String): Boolean =
    listOf(Language.JAVA, Language.KOTLIN, Language.PYTHON).any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter =
    if (json == null) EvaluationFilter.ACCEPT_ALL
    else TypeFilter((json as List<String>).map { TypeProperty.valueOf(it) })

  override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
}
