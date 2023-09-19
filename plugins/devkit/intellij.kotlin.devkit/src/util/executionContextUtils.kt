// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal fun getContext(element: PsiElement): ExecutionContextType {
  val containingLambda = element.getParentOfType<KtLambdaExpression>(false)

  // if lambda is null, check the containing method
  if (containingLambda == null) {
    val containingMethod = element.getParentOfType<KtNamedFunction>(false)
    return if (containingMethod.hasSuspendModifier()) ExecutionContextType.SUSPENDING_FUNCTION else ExecutionContextType.BLOCKING
  }

  // otherwise, check containing argument (whether the corresponding parameter has `suspend` modifier)
  val containingArgument = containingLambda.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
  if (containingArgument != null) {
    val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return ExecutionContextType.BLOCKING
    analyze(callExpression) {
      val functionCall = callExpression.resolveCall()?.singleFunctionCallOrNull() ?: return ExecutionContextType.BLOCKING
      val lambdaArgumentType = functionCall.argumentMapping[containingLambda]?.returnType ?: return ExecutionContextType.BLOCKING

      return if (lambdaArgumentType.isSuspendFunctionType) ExecutionContextType.SUSPENDING_LAMBDA else ExecutionContextType.BLOCKING
    }
  }

  // otherwise, check if it's a property or a function
  val containingPropertyOrFunction: KtCallableDeclaration? = containingLambda.getParentOfTypes(true, KtProperty::class.java,
                                                                                               KtNamedFunction::class.java)
  if (containingPropertyOrFunction?.typeReference.hasSuspendModifier()) return ExecutionContextType.SUSPENDING_LAMBDA
  return if (containingPropertyOrFunction.hasSuspendModifier()) ExecutionContextType.SUSPENDING_LAMBDA else ExecutionContextType.BLOCKING
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
