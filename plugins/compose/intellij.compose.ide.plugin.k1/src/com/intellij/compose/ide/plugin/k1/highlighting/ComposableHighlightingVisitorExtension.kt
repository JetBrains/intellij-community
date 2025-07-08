// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k1.highlighting

import androidx.compose.compiler.plugins.kotlin.k1.hasComposableAnnotation
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.highlighting.COMPOSABLE_CALL_TEXT_TYPE
import com.intellij.compose.ide.plugin.shared.isComposeEnabledForElementModule
import com.intellij.compose.ide.plugin.shared.isElementInLibrarySource
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingVisitorExtension
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Used to apply styles for calls to @Composable functions.
 *
 * JetBrains documentation recommends doing highlighting such as this using
 * [com.intellij.lang.annotation.Annotator] (guidance available at
 * https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html#annotator).
 * But it turns out that the Kotlin plugin is running its syntax highlighting using a different
 * mechanism which can run in parallel with annotators. That doesn't matter for annotators that
 * don't conflict with the built-in highlighting, but in the case of Compose we are overriding some
 * of the standard function colors, and so we need to ensure that Compose highlighting takes
 * precedence.
 *
 * Luckily, the Kotlin plugin provides its own extension mechanism, which is implemented here with
 * [KotlinHighlightingVisitorExtension]. When this code returns Composable function highlighting for
 * a given function call, it will always be used instead of the default Kotlin highlighting.
 *
 * For K2 implementation
 * @see com.intellij.compose.ide.plugin.k2.highlighting.ComposableFunctionCallHighlighterExtension
 */
internal class ComposableHighlightingVisitorExtension : KotlinHighlightingVisitorExtension() {
  override fun highlightDeclaration(
    elementToHighlight: PsiElement,
    descriptor: DeclarationDescriptor,
  ): HighlightInfoType? = null

  override fun highlightCall(
    elementToHighlight: PsiElement,
    resolvedCall: ResolvedCall<*>,
  ): HighlightInfoType? {
    if (!resolvedCall.isComposableInvocation()) return null

    return if (isComposeEnabledForElementModule(elementToHighlight) || isElementInLibrarySource(elementToHighlight))
      COMPOSABLE_CALL_TEXT_TYPE
    else null
  }

  private fun ResolvedCall<*>.isComposableInvocation(): Boolean {
    if (this is VariableAsFunctionResolvedCall) {
      if (variableCall.candidateDescriptor.type.hasComposableAnnotation()) return true
      if (functionCall.resultingDescriptor.hasComposableAnnotation()) return true
      return false
    }
    val candidateDescriptor = candidateDescriptor
    if (candidateDescriptor is FunctionDescriptor) {
      if (candidateDescriptor.isOperator &&
          candidateDescriptor.name == OperatorNameConventions.INVOKE
      ) {
        if (dispatchReceiver?.type?.hasComposableAnnotation() == true) {
          return true
        }
      }
    }
    return when (candidateDescriptor) {
      is ValueParameterDescriptor -> false
      is LocalVariableDescriptor -> false
      is PropertyDescriptor -> {
        val isGetter = valueArguments.isEmpty()
        val getter = candidateDescriptor.getter
        if (isGetter && getter != null) {
          getter.hasComposableAnnotation()
        } else {
          false
        }
      }
      is PropertyGetterDescriptor -> candidateDescriptor.hasComposableAnnotation()
      else -> candidateDescriptor.hasComposableAnnotation()
    }
  }
}
