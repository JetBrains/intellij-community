package com.intellij.compose.ide.plugin.k1

import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptor
import org.jetbrains.kotlin.idea.inspections.AbstractRangeInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtExpression

internal class K1ComposeColorLineMarkerProviderDescriptor : ComposeColorLineMarkerProviderDescriptor() {

  override fun KtExpression.evaluateToConstantOrNullImpl(): Any? {
    return constantValueOrNull()?.value
  }
}
