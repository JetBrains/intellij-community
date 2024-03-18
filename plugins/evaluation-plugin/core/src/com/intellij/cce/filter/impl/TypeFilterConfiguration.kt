// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class TypeFilter(val values: List<TypeProperty>) : EvaluationFilter {
  override fun shouldEvaluate(code: CodeToken): Boolean =
    code.properties.tokenType == TypeProperty.UNKNOWN || values.contains(code.properties.tokenType)
  override fun toJson(): JsonElement {
    val json = JsonArray()
    for (value in values)
      json.add(JsonPrimitive(value.name))
    return json
  }
}

class TypeFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "statementTypes"
  override val description: String = "Filter out tokens by statement type"
  override val hasUI: Boolean = true

  override fun isLanguageSupported(languageName: String): Boolean = listOf(
    Language.JAVA,
    Language.KOTLIN,
    Language.PYTHON,
    Language.JS,
    Language.TYPESCRIPT,
    Language.RUBY,
    Language.GO,
  ).any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter =
    if (json == null) EvaluationFilter.ACCEPT_ALL
    else TypeFilter((json as List<String>).map { TypeProperty.valueOf(it) })

  override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
}
