// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.util

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.kotlin.util.ExecutionContextType.BLOCKING
import org.jetbrains.idea.devkit.kotlin.util.ExecutionContextType.SUSPENDING_FUNCTION
import org.jetbrains.idea.devkit.kotlin.util.ExecutionContextType.SUSPENDING_LAMBDA
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal fun getContext(element: PsiElement): ExecutionContextType {
  val suspendingLambda = element.findParentSuspendingLambda()
  if (suspendingLambda != null) {
    return SUSPENDING_LAMBDA
  }

  // if lambda is null, check the containing method
  val containingMethod = element.getParentOfType<KtNamedFunction>(false)
  return if (containingMethod.hasSuspendModifier()) SUSPENDING_FUNCTION else BLOCKING
}

private fun PsiElement.findParentSuspendingLambda(): KtLambdaExpression? {
  return this.getParentOfTypesAndPredicate(false, KtLambdaExpression::class.java) {
    // check containing argument (whether the corresponding parameter has `suspend` modifier)
    val containingArgument = it.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
    if (containingArgument != null) {
      val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return@getParentOfTypesAndPredicate false
      analyze(callExpression) {
        val functionCall = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return@getParentOfTypesAndPredicate false
        val lambdaArgumentType = functionCall.argumentMapping[it]?.returnType ?: return@getParentOfTypesAndPredicate false
        return@getParentOfTypesAndPredicate lambdaArgumentType.isSuspendFunctionType
      }
    }

    // otherwise, check if it's a property or a function
    val containingPropertyOrFunction: KtCallableDeclaration? = it.getParentOfTypes(true, KtProperty::class.java,
                                                                                   KtNamedFunction::class.java)
    if (containingPropertyOrFunction?.typeReference.hasSuspendModifier()) return@getParentOfTypesAndPredicate true
    return@getParentOfTypesAndPredicate containingPropertyOrFunction.hasSuspendModifier()
  }
}

private fun KtModifierListOwner?.hasSuspendModifier(): Boolean {
  return this?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
}

internal enum class ExecutionContextType {
  BLOCKING,
  SUSPENDING_FUNCTION,
  SUSPENDING_LAMBDA;

  fun isSuspending(): Boolean {
    return this != BLOCKING
  }
}
