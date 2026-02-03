// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.filter

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.intellij.cce.core.CodeToken

interface EvaluationFilter {
  companion object {
    val ACCEPT_ALL = object : EvaluationFilter {
      override fun shouldEvaluate(code: CodeToken): Boolean = true
      override fun toJson(): JsonElement = JsonNull.INSTANCE
    }
  }

  fun shouldEvaluate(code: CodeToken): Boolean

  fun toJson(): JsonElement
}
