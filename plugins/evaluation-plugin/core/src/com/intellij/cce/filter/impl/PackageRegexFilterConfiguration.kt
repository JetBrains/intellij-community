// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.PropertyAdapters
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class PackageRegexFilter(pattern: String) : EvaluationFilter {
  private val regex = Regex(pattern)
  override fun shouldEvaluate(code: CodeToken): Boolean {
    return PropertyAdapters.Jvm.adapt(code.properties)?.packageName?.matches(regex) ?: false
  }

  override fun toJson(): JsonElement = JsonPrimitive(regex.pattern)
}

class PackageRegexFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "packageRegex"
  override val description: String = "Filter out tokens by package name regex"
  override val hasUI: Boolean = true

  override fun isLanguageSupported(languageName: String): Boolean = listOf(Language.JAVA,
                                                                           Language.KOTLIN).any { it.displayName == languageName }

  override fun buildFromJson(json: Any?): EvaluationFilter = if (json == null) EvaluationFilter.ACCEPT_ALL
  else PackageRegexFilter(json as String)

  override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
}