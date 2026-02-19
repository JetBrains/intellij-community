// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class FeaturesFilter(val values: List<String>) : EvaluationFilter {
  override fun shouldEvaluate(code: CodeToken): Boolean {
    return values.any { code.properties.hasFeature(it) }
  }

  override fun toJson(): JsonElement {
    val json = JsonArray()
    for (value in values)
      json.add(JsonPrimitive(value))
    return json
  }
}

class FeaturesFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "features"
  override val description: String = "Filter out session if it doesn't contain concrete features"
  override val hasUI: Boolean = false

  override fun isLanguageSupported(languageName: String): Boolean = Language.entries.any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter = if (json == null) EvaluationFilter.ACCEPT_ALL
  else FeaturesFilter(json as List<String>)

  override fun defaultFilter(): EvaluationFilter = FeaturesFilter(emptyList())
}