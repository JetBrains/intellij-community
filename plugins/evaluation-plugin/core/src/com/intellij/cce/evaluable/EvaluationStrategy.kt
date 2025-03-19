// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.filter.EvaluationFilter

interface EvaluationStrategy {

  val filters: Map<String, EvaluationFilter>

  companion object {
    val defaultStrategy = object : EvaluationStrategy {
      override val filters: Map<String, EvaluationFilter> = mapOf()
    }
  }
}
