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
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

fun KtElement.expectedComposableAnnotationHolder(): KtModifierListOwner? = composableHolderAndScope()?.first

private fun KtElement.composableHolderAndScope(): Pair<KtModifierListOwner, KtExpression>? =
  when (val scope = possibleComposableScope()) {
    is KtNamedFunction -> scope to scope
    is KtPropertyAccessor -> scope.takeIf { it.isGetter }?.let { it to it }
    is KtLambdaExpression -> {
      when (val lambdaParent = scope.parent) {
        is KtValueArgument ->
          when (val resolution = lambdaParent.resolveComposableLambdaArgument()) {
            is ComposableResolution.Recurse -> lambdaParent.composableHolderAndScope()
            is ComposableResolution.Resolved -> resolution.parameter.typeReference?.let { it to scope }
            null -> null
          }
        is KtProperty if lambdaParent.typeReference != null -> lambdaParent.typeReference!! to scope
        is KtReturnExpression -> {
          val enclosingFunction = lambdaParent.parentOfType<KtNamedFunction>()
          enclosingFunction?.typeReference?.let { it to scope }
        }
        is KtNamedFunction -> {
          lambdaParent.typeReference?.let { it to scope } ?: (scope.functionLiteral to scope)
        }
        else -> scope.functionLiteral to scope
      }
    }
    else -> null
  }

private tailrec fun KtElement.possibleComposableScope(): KtExpression? {
  return when (val p = parent) {
    is KtClassInitializer -> null

    is KtNamedFunction,
    is KtPropertyAccessor,
    is KtLambdaExpression -> p

    is KtElement -> p.possibleComposableScope()
    else -> null
  }
}

private sealed class ComposableResolution {
  data object Recurse : ComposableResolution()
  data class Resolved(val parameter: KtParameter) : ComposableResolution()
}

private fun KtValueArgument.resolveComposableLambdaArgument(): ComposableResolution? = analyze(this) {
  val callExpr = parentOfType<KtCallExpression>() ?: return null
  val call = callExpr.resolveToCall()?.successfulFunctionCallOrNull() ?: return resolveFromFunctionTypedVariable(callExpr)

  val argExpr = getArgumentExpression()
  val paramSymbol = argExpr?.let { call.valueArgumentMapping[it]?.symbol }
  if (paramSymbol == null || paramSymbol.psi == null) return resolveFromFunctionTypedVariable(callExpr)

  val functionSymbol = call.symbol as? KaNamedFunctionSymbol

  if (functionSymbol?.isInline == true && !paramSymbol.isNoinline && !paramSymbol.isCrossinline) ComposableResolution.Recurse
  else (paramSymbol.psi as? KtParameter)?.let { ComposableResolution.Resolved(it) }
}

private fun KtValueArgument.resolveFromFunctionTypedVariable(callExpr: KtCallExpression): ComposableResolution? {
  val calleeExpression = callExpr.calleeExpression as? KtReferenceExpression ?: return null
  val variablePsi = calleeExpression.mainReference.resolve() as? KtProperty ?: return null
  val function = variablePsi.initializer as? KtFunction ?: return null

  return function.getParameterForArgument(this)?.let { ComposableResolution.Resolved(it) }
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