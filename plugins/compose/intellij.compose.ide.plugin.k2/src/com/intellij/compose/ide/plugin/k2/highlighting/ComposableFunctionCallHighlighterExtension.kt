// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_CLASS_ID
import com.intellij.compose.ide.plugin.shared.highlighting.COMPOSABLE_CALL_TEXT_TYPE
import com.intellij.compose.ide.plugin.shared.isComposeEnabledForElementModule
import com.intellij.compose.ide.plugin.shared.isElementInLibrarySource
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Used to apply styles for calls to @Composable functions.
 *
 * For K1 implementation
 * @see com.intellij.compose.ide.plugin.k1.highlighting.ComposableHighlightingVisitorExtension
 */
internal class ComposableFunctionCallHighlighterExtension : KotlinCallHighlighterExtension {
  override fun KaSession.highlightCall(elementToHighlight: PsiElement, call: KaCall): HighlightInfoType? {
    val memberCall = call as? KaCallableMemberCall<*, *> ?: return null
    if (!isComposableInvocation(memberCall)) return null

    return if (isComposeEnabledForElementModule(elementToHighlight) || isElementInLibrarySource(elementToHighlight))
      COMPOSABLE_CALL_TEXT_TYPE
    else null
  }

  @OptIn(KaExperimentalApi::class)
  private fun isComposableInvocation(memberCall: KaCallableMemberCall<*, *>): Boolean {
    fun hasComposableAnnotation(annotated: KaAnnotated?): Boolean {
      return annotated != null && COMPOSABLE_ANNOTATION_CLASS_ID in annotated.annotations
    }

    fun KaNamedFunctionSymbol.isInvokeOperatorCall() : Boolean {
      return this.isOperator && this.name == OperatorNameConventions.INVOKE
    }

    return when (val callableSymbol = memberCall.symbol) {
      is KaNamedFunctionSymbol -> {
        if (hasComposableAnnotation(callableSymbol)) return true

        if (callableSymbol.isInvokeOperatorCall()) {
          val typeInvokeOperatorIsCalledOn = memberCall.partiallyAppliedSymbol.dispatchReceiver?.type ?: return false
          return hasComposableAnnotation(typeInvokeOperatorIsCalledOn)
        }

        return false
      }
      is KaPropertySymbol -> hasComposableAnnotation(callableSymbol.getter)
      else -> false
    }
  }
}
