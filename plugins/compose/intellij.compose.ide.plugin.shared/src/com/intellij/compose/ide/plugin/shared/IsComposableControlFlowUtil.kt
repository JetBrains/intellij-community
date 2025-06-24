// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries

/**
 * Checks whether [this] is inside a Composable control flow.
 * Examples:
 * ```kt
 * fun bar() {
 *     somePsiElement // <-- Will be false for this element. Because it would be a compiler error to call @Composable functions here.
 * }
 *
 * @Composable fun foo() {
 *     somePsiElement // <-- Will be true for this element. Because it's legal to call @Composable functions here.
 * }
 * ```
 * For detailed behavior, see: [com.intellij.compose.ide.plugin.shared.ComposableAnnotationToExtractedFunctionAddingAnalyserTest]
 */
internal fun PsiElement?.isInsideComposableControlFlow(): Boolean = when (this) {
  null -> false // Reached root parent
  is KtPropertyAccessor, is KtNamedFunction -> hasComposableAnnotation()
  is KtLambdaExpression -> analyze(this) { ownsComposableControlFlow() }
  is KtLambdaArgument -> analyze(this) { ownsComposableControlFlow() }
  else -> parent.isInsideComposableControlFlow()
}

context(KaSession)
private fun KtLambdaExpression.ownsComposableControlFlow(): Boolean =
  (parent as? KtLambdaArgument)?.ownsComposableControlFlow() // Only check lambda that is NOT an argument
  // One would simply expect us to infer the type and then check whether the type has a `@Composable` annotation.
  // But currently, annotations are not inferred. See https://jetbrains.slack.com/archives/C061DS4G41J/p1738180610032509
  ?: getAnnotationEntries().containsAnnotationNamed(COMPOSABLE_ANNOTATION_FQ_NAME)

context(KaSession)
private fun KtLambdaArgument.ownsComposableControlFlow(): Boolean =
  (parent as? KtCallExpression)
    ?.resolveToCall()
    .let { functionCall -> functionCall != null && this.ownsComposableControlFlowWhenArgumentOf(functionCall) }

context(KaSession)
private fun KtLambdaArgument.ownsComposableControlFlowWhenArgumentOf(functionCall: KaCallInfo): Boolean =
  this.hasAnnotationIn(functionCall, COMPOSABLE_ANNOTATION_FQ_NAME) || // We are explicitly in Composable flow!
  (
    !this.hasAnnotationIn(functionCall, DISALLOW_COMPOSABLE_CALLS_FQ_NAME) // If our flow is not explicitly forbidding Composable calls,
    && this.isInlinedInside(functionCall) // and we are in an inlined lambda,
    && parent.isInsideComposableControlFlow() // then check whether the parent is in Composable flow
  )

private fun KtLambdaArgument.hasAnnotationIn(function: KaCallInfo, fqName: FqName): Boolean = analyze(this) {
  function.parameterSymbolOf(this@hasAnnotationIn)
    ?.returnType
    ?.isAnnotatedWith(fqName) // Not cached, but doesn't matter for this infrequently triggered extension
    ?: false
}

private fun KaAnnotated.isAnnotatedWith(fqName: FqName): Boolean =
  annotations.any { it.classId?.asSingleFqName() == fqName }

context(KaSession)
private fun KtLambdaArgument.isInlinedInside(function: KaCallInfo): Boolean =
  function.isInline() &&
    function.parameterSymbolOf(this@isInlinedInside)
      ?.let { !it.isNoinline && !it.isCrossinline }
      ?: true

private fun KaCallInfo.parameterSymbolOf(argument: KtLambdaArgument): KaValueParameterSymbol? =
  argument.getArgumentExpression()?.let { expression ->
    successfulFunctionCallOrNull()
      ?.argumentMapping
      ?.get(expression)
      ?.symbol
  }

private fun KaCallInfo.isInline(): Boolean =
  successfulFunctionCallOrNull()
    ?.symbol
    ?.let { it as? KaNamedFunctionSymbol }
    ?.isInline
    ?: false

/** Not cached, but doesn't matter for this infrequently triggered extension */
context(KaSession)
private fun Iterable<KtAnnotationEntry>.containsAnnotationNamed(fqName: FqName): Boolean =
  any { it.resolveToCall()?.singleConstructorCallOrNull()?.symbol?.containingClassId?.asSingleFqName() == fqName }
