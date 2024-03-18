// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class MinimumOffsetFilter(val offset: Int) : EvaluationFilter {
  override fun shouldEvaluate(code: CodeToken): Boolean {
    return code.offset >= offset
  }

  override fun toJson(): JsonElement {
    return JsonPrimitive(offset)
  }
}

class MinimumOffsetFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "minimumOffset"
  override val description: String = "Filter out session if it has offset more than given value"
  override val hasUI: Boolean = false

  override fun isLanguageSupported(languageName: String): Boolean = Language.entries.any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter = if (json == null) EvaluationFilter.ACCEPT_ALL
  else MinimumOffsetFilter((json as Double).toInt())

  override fun defaultFilter(): EvaluationFilter = FeaturesFilter(emptyList())
}