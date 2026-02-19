// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.PropertyAdapters
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class StaticFilter(private val expectedValue: Boolean) : EvaluationFilter {
  override fun shouldEvaluate(code: CodeToken): Boolean {
    return PropertyAdapters.Jvm.adapt(code.properties)?.isStatic == expectedValue
  }

  override fun toJson(): JsonElement = JsonPrimitive(expectedValue)
}

class StaticFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "isStatic"
  override val description: String = "Filter out token if it's static member access"
  override val hasUI: Boolean = true

  override fun isLanguageSupported(languageName: String): Boolean = listOf(Language.JAVA,
                                                                           Language.KOTLIN).any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter = if (json == null) EvaluationFilter.ACCEPT_ALL
  else StaticFilter(json as Boolean)

  override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
}