// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

object RemoveEmptyParenthesesFromLambdaCallUtils {
    fun canRemoveByPsi(list: KtValueArgumentList): Boolean {
        if (list.arguments.isNotEmpty()) return false
        val parent = list.parent as? KtCallExpression ?: return false
        if (parent.calleeExpression?.text == KtTokens.SUSPEND_KEYWORD.value) return false
        val singleLambdaArgument = parent.lambdaArguments.singleOrNull() ?: return false
        if (list.getLineNumber(start = false) != singleLambdaArgument.getLineNumber(start = true)) return false
        val prev = list.getPrevSiblingIgnoringWhitespaceAndComments()
        return prev !is KtCallExpression && (prev as? KtQualifiedExpression)?.selectorExpression !is KtCallExpression
    }

    fun removeArgumentList(list: KtValueArgumentList) {
        list.delete()
    }

    fun removeEmptyArgumentListIfApplicable(list: KtValueArgumentList) {
        if (canRemoveByPsi(list)) {
            removeArgumentList(list)
        }
    }
}