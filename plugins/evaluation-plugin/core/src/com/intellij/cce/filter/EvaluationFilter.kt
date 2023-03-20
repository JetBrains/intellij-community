package com.intellij.cce.filter

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.intellij.cce.core.TokenProperties

interface EvaluationFilter {
  companion object {
    val ACCEPT_ALL = object : EvaluationFilter {
      override fun shouldEvaluate(properties: TokenProperties): Boolean = true
      override fun toJson(): JsonElement = JsonNull.INSTANCE
    }
  }

  fun shouldEvaluate(properties: TokenProperties): Boolean

  fun toJson(): JsonElement
}
