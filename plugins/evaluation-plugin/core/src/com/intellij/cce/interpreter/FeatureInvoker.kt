package com.intellij.cce.interpreter

import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties

interface FeatureInvoker {
  fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session

  fun comparator(generated: String, expected: String, ): Boolean
}
