// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter

interface EvaluationFilterConfiguration {
  interface Configurable<T> {
    fun build(): EvaluationFilter

    val view: T
  }

  val id: String

  val description: String

  val hasUI: Boolean

  fun isLanguageSupported(languageName: String): Boolean

  fun buildFromJson(json: Any?): EvaluationFilter

  fun defaultFilter(): EvaluationFilter
}
