/*
 * Copyright (C) 2019 The Android Open Source Project
 *  Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

fun KtElement.expectedComposableAnnotationHolder(): KtModifierListOwner? = composableHolderAndScope()?.first

private tailrec fun KtElement.composableHolderAndScope(): Pair<KtModifierListOwner, KtExpression>? {
  when (val scope = possibleComposableScope()) {
    is KtNamedFunction -> return scope to scope
    is KtPropertyAccessor -> return scope.takeIf { it.isGetter }?.let { it to it }
    is KtLambdaExpression -> {
      val lambdaParent = scope.parent
      if (lambdaParent is KtValueArgument) {
        val resolution = lambdaParent.resolveComposableLambdaArgument()
        return when (resolution) {
          is ComposableResolution.Recurse -> lambdaParent.composableHolderAndScope()
          is ComposableResolution.Resolved -> resolution.parameter.typeReference?.let { it to scope }
          null -> null
        }
      }
      if (lambdaParent is KtProperty) {
        lambdaParent.typeReference?.let { return it to scope }
      }
      return scope.functionLiteral to scope
    }
  }
  return null
}

private fun KtElement.possibleComposableScope(): KtExpression? {
  var current: PsiElement? = parent
  while (current != null) {
    if (current is KtClassInitializer) return null
    if (current is KtNamedFunction || current is KtPropertyAccessor || current is KtLambdaExpression) {
      return current as? KtExpression
    }
    current = current.parent
  }
  return null
}

private sealed class ComposableResolution {
  data object Recurse : ComposableResolution()
  data class Resolved(val parameter: KtParameter) : ComposableResolution()
}

private fun KtValueArgument.resolveComposableLambdaArgument(): ComposableResolution? = analyze(this) {
  val callExpr = parentOfType<KtCallExpression>() ?: return null
  val call = callExpr.resolveToCall()?.successfulFunctionCallOrNull()

  if (call != null) {
    val argExpr = getArgumentExpression()
    val paramSymbol = argExpr?.let { call.argumentMapping[it]?.symbol }
    if (paramSymbol != null && paramSymbol.psi != null) {
      val functionSymbol = call.symbol as? KaNamedFunctionSymbol

      return if (functionSymbol?.isInline == true && !paramSymbol.isNoinline) {
        ComposableResolution.Recurse
      }
      else {
        (paramSymbol.psi as? KtParameter)?.let { ComposableResolution.Resolved(it) }
      }
    }
  }
  val calleeExpression = callExpr.calleeExpression as? KtReferenceExpression ?: return null
  val variableSymbol = calleeExpression.mainReference.resolveToSymbols().firstOrNull()

  val variablePsi = variableSymbol?.psi as? KtProperty
  val initializer = variablePsi?.initializer

  (initializer as? KtFunction)
    ?.getParameterForArgument(this@resolveComposableLambdaArgument)
    ?.let { return ComposableResolution.Resolved(it) }

  null
}

private fun KtFunction.getParameterForArgument(argument: KtValueArgument): KtParameter? {
  if (argument is KtLambdaArgument) return valueParameters.lastOrNull()

  val argumentName = argument.getArgumentName()?.asName?.asString()
  if (argumentName != null) return valueParameters.firstOrNull { it.name == argumentName }

  return (argument.parent as? KtValueArgumentList)
    ?.arguments
    ?.indexOf(argument)
    ?.let(valueParameters::getOrNull)
}