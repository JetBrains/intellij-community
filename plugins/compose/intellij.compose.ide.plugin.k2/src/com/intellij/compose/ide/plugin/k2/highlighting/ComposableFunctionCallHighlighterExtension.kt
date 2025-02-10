// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_CLASS_ID
import com.intellij.compose.ide.plugin.shared.highlighting.COMPOSABLE_CALL_TEXT_TYPE
import com.intellij.compose.ide.plugin.shared.isComposeEnabledInModule
import com.intellij.compose.ide.plugin.shared.isElementInLibrarySource
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension

internal class ComposableFunctionCallHighlighterExtension : KotlinCallHighlighterExtension {
  context(KaSession)
  override fun highlightCall(elementToHighlight: PsiElement, call: KaCall): HighlightInfoType? {
    val memberCall = call as? KaCallableMemberCall<*, *> ?: return null
    val callableSymbol = memberCall.symbol
    if (!isComposableInvocation(callableSymbol)) return null

    return if (isComposeEnabledInModule(elementToHighlight) || isElementInLibrarySource(elementToHighlight))
      COMPOSABLE_CALL_TEXT_TYPE
    else null
  }

  @OptIn(KaExperimentalApi::class)
  private fun isComposableInvocation(callableSymbol: KaCallableSymbol): Boolean {
    fun hasComposableAnnotation(annotated: KaAnnotated?): Boolean {
      return annotated != null && COMPOSABLE_ANNOTATION_CLASS_ID in annotated.annotations
    }

    val type = callableSymbol.returnType
    if (hasComposableAnnotation(type)) return true

    return when (callableSymbol) {
      is KaNamedFunctionSymbol -> hasComposableAnnotation(callableSymbol)
      is KaPropertySymbol -> hasComposableAnnotation(callableSymbol.getter)
      else -> false
    }
  }
}
