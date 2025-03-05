// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

object RemoveExplicitTypeArgumentsUtils {
    fun isApplicableByPsi(callExpression: KtCallExpression): Boolean {
        val typeArguments = callExpression.typeArguments
        if (typeArguments.isEmpty()) return false
        return typeArguments.none { it.typeReference?.isAnnotatedDeep() == true }
    }

    fun applyTo(typeArgumentList: KtTypeArgumentList) {
        val prevCallExpression = typeArgumentList.getPrevSiblingIgnoringWhitespaceAndComments() as? KtCallExpression
        val isBetweenLambdaArguments = prevCallExpression?.lambdaArguments?.isNotEmpty() == true &&
                typeArgumentList.getNextSiblingIgnoringWhitespaceAndComments() is KtLambdaArgument

        typeArgumentList.delete()

        if (isBetweenLambdaArguments) {
            prevCallExpression.replace(KtPsiFactory(typeArgumentList.project).createExpressionByPattern("($0)", prevCallExpression))
        }
    }
}