// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

object RemoveEmptyParenthesesFromLambdaCallApplicator {
    val applicator = applicator<KtValueArgumentList, KotlinApplicatorInput.Empty> {
        isApplicableByPsi { list: KtValueArgumentList ->
            if (list.arguments.isNotEmpty()) return@isApplicableByPsi false
            val parent = list.parent as? KtCallExpression ?: return@isApplicableByPsi false
            if (parent.calleeExpression?.text == KtTokens.SUSPEND_KEYWORD.value) return@isApplicableByPsi false
            val singleLambdaArgument = parent.lambdaArguments.singleOrNull() ?: return@isApplicableByPsi false
            if (list.getLineNumber(start = false) != singleLambdaArgument.getLineNumber(start = true)) return@isApplicableByPsi false
            val prev = list.getPrevSiblingIgnoringWhitespaceAndComments()
            prev !is KtCallExpression && (prev as? KtQualifiedExpression)?.selectorExpression !is KtCallExpression
        }
        applyTo { list: KtValueArgumentList, _ -> list.delete() }
        familyAndActionName { KotlinBundle.message("remove.unnecessary.parentheses.from.function.call.with.lambda") }
    }
}