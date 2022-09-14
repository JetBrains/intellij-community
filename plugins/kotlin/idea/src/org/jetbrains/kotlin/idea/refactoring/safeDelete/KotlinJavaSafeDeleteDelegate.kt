// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReference
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinJavaSafeDeleteDelegate : JavaSafeDeleteDelegate {
    override fun createUsageInfoForParameter(
        reference: PsiReference, usages: MutableList<UsageInfo>, parameter: PsiParameter, method: PsiMethod
    ) {
        if (reference !is KtReference) return

        val element = reference.element

        val callExpression = element.getNonStrictParentOfType<KtCallExpression>() ?: return

        val calleeExpression = callExpression.calleeExpression
        if (!(calleeExpression is KtReferenceExpression && calleeExpression.isAncestor(element))) return

        val target = reference.resolve() ?: return

        val originalDeclaration = method.unwrapped
        if (originalDeclaration !is PsiMethod && originalDeclaration !is KtDeclaration) return

        if (originalDeclaration != target) {
            return
        }

        val args = callExpression.valueArguments

        val namedArguments = args.filter { arg -> arg is KtValueArgument && arg.getArgumentName()?.text == parameter.name }
        if (namedArguments.isNotEmpty()) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, namedArguments.first()))
            return
        }

        val originalParameter = parameter.unwrapped ?: return

        val parameterIndex = originalParameter.parameterIndex()
        if (parameterIndex < 0) return

        val argCount = args.size
        if (parameterIndex < argCount) {
            usages.add(SafeDeleteValueArgumentListUsageInfo(parameter, args[parameterIndex] as KtValueArgument))
        } else {
            val lambdaArgs = callExpression.lambdaArguments
            val lambdaIndex = parameterIndex - argCount
            if (lambdaIndex < lambdaArgs.size) {
                usages.add(SafeDeleteReferenceSimpleDeleteUsageInfo(lambdaArgs[lambdaIndex], parameter, true))
            }
        }
    }
}
