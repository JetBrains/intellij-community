// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.CancellationCheckProvider
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode


class KotlinCancellationCheckProvider : CancellationCheckProvider {

  override fun findContext(element: PsiElement): CancellationCheckProvider.Context {
    val containingLambda = element.getParentOfType<KtLambdaExpression>(false)

    // if lambda is null, check the containing method
    if (containingLambda == null) {
      val containingMethod = element.getParentOfType<KtNamedFunction>(false)
      return if (containingMethod.hasSuspendModifier()) CancellationCheckProvider.Context.SUSPENDING else CancellationCheckProvider.Context.BLOCKING
    }

    // otherwise, check containing argument (whether the corresponding parameter has `suspend` modifier)
    val containingArgument = containingLambda.getParentOfType<KtValueArgument>(true, KtCallableDeclaration::class.java)
    if (containingArgument != null) {
      val callExpression = containingArgument.getStrictParentOfType<KtCallExpression>() ?: return CancellationCheckProvider.Context.BLOCKING
      val resolvedCall = callExpression.resolveToCall(BodyResolveMode.PARTIAL) ?: return CancellationCheckProvider.Context.BLOCKING

      val parameterForArgument = resolvedCall.getParameterForArgument(containingArgument) ?: return CancellationCheckProvider.Context.BLOCKING
      val type = parameterForArgument.returnType ?: return CancellationCheckProvider.Context.BLOCKING
      return if (type.isSuspendFunctionType) CancellationCheckProvider.Context.SUSPENDING else CancellationCheckProvider.Context.BLOCKING
    }

    // otherwise, check if it's a property or a function
    val containingPropertyOrFunction: KtCallableDeclaration? =
      containingLambda.getParentOfTypes(true, KtProperty::class.java, KtNamedFunction::class.java)
    if (containingPropertyOrFunction?.typeReference.hasSuspendModifier()) return CancellationCheckProvider.Context.SUSPENDING
    return if (containingPropertyOrFunction.hasSuspendModifier()) CancellationCheckProvider.Context.SUSPENDING else CancellationCheckProvider.Context.BLOCKING
  }

  private fun KtModifierListOwner?.hasSuspendModifier(): Boolean {
    return this?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true
  }

}
