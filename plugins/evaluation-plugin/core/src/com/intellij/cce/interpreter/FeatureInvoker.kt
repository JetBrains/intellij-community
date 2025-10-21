package com.intellij.cce.interpreter

import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties

interface AsyncFeatureInvoker {
  suspend fun call(expectedText: String, offset: Int, properties: TokenProperties, sessionId: String): Session
}

interface FeatureInvoker : AsyncFeatureInvoker {
  fun callFeature(expectedText: String, offset: Int, properties: TokenProperties, sessionId: String): Session

  fun comparator(generated: String, expected: String, ): Boolean

  override suspend fun call(expectedText: String, offset: Int, properties: TokenProperties, sessionId: String): Session {
    return callFeature(expectedText, offset, properties, sessionId)
  }
}
