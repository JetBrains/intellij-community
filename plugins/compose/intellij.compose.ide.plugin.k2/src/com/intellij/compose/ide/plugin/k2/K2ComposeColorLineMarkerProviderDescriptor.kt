package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtExpression

internal class K2ComposeColorLineMarkerProviderDescriptor : ComposeColorLineMarkerProviderDescriptor() {

  override fun KtExpression.evaluateToConstantOrNullImpl(): Any? {
    return analyze(this) {
      evaluate()?.value
    }
  }
}
