// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.CancellationCheckProvider
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType


private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val COROUTINE_CHECK_CANCELLED = "com.intellij.openapi.progress.checkCancelled"

class KtCancellationCheckProvider : CancellationCheckProvider {

  enum class Context {
    BLOCKING, SUSPENDING
  }

  override fun findCancellationCheckCall(element: PsiElement): String {
    return when (findContext(element)) {
      Context.BLOCKING -> PROGRESS_MANAGER_CHECKED_CANCELED
      Context.SUSPENDING -> COROUTINE_CHECK_CANCELLED
    }
  }

  override fun isCancellationCheckCall(element: PsiElement?, cancellationCheckFqn: String): Boolean {
    val callExpression = when (element) {
      is KtCallExpression -> element
      is KtDotQualifiedExpression -> element.getChildOfType<KtCallExpression>() ?: return false
      else -> return false
    }

    analyze(callExpression) {
      val functionCalledSymbol = callExpression.resolveCall().singleFunctionCallOrNull()?.symbol ?: return false
      return functionCalledSymbol.callableIdIfNonLocal?.asSingleFqName() == FqName(cancellationCheckFqn)
    }
  }

  private fun findContext(element: PsiElement): Context {
    val containingLambda = element.getParentOfType<KtLambdaExpression>(false)

    // if lambda is null, check the containing method
    if (containingLambda == null) {
      val containingMethod = element.getParentOfType<KtNamedFunction>(false)
      return if (containingMethod.hasSuspendModifier()) Context.SUSPENDING else Context.BLOCKING
    }

    // otherwise, check containing argument (whether the corresponding parameter has `suspend` modifier)
    val containingArgument = containingLambda.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
    if (containingArgument != null) {
      val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return Context.BLOCKING
      analyze(callExpression) {
        val functionCall = callExpression.resolveCall().singleFunctionCallOrNull() ?: return Context.BLOCKING
        val lambdaArgumentType = functionCall.argumentMapping[containingLambda]?.returnType ?: return Context.BLOCKING

        return if (lambdaArgumentType.isSuspendFunctionType) Context.SUSPENDING else Context.BLOCKING
      }
    }

    // otherwise, check if it's a property or a function
    val containingPropertyOrFunction: KtCallableDeclaration? = containingLambda.getParentOfTypes(true, KtProperty::class.java,
                                                                                                 KtNamedFunction::class.java)
    if (containingPropertyOrFunction?.typeReference.hasSuspendModifier()) return Context.SUSPENDING
    return if (containingPropertyOrFunction.hasSuspendModifier()) Context.SUSPENDING else Context.BLOCKING
  }

  private fun KtModifierListOwner?.hasSuspendModifier(): Boolean {
    return this?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
  }

}
