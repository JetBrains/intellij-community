// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.branches
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

object UnfoldFunctionCallToIfOrWhenUtils {
    inline fun <reified T: KtExpression> canUnfold(element: KtCallExpression): Boolean {
        if (element.calleeExpression == null) return false
        val (argument, _) = element.argumentOfType<T>() ?: return false
        val branches = branches(argument) ?: return false
        return branches.all { it.singleExpression() != null }
    }

    inline fun <reified T: KtExpression> unfold(element: KtCallExpression) {
        val (argument, argumentIndex) = element.argumentOfType<T>() ?: return
        val branches = branches(argument) ?: return
        val qualifiedOrCall = element.getQualifiedExpressionForSelectorOrThis()
        branches.forEach {
            val branchExpression = it.singleExpression() ?: return@forEach
            val copied = qualifiedOrCall.copy()
            val valueArguments = when (copied) {
                is KtCallExpression -> copied.valueArguments
                is KtQualifiedExpression -> copied.callExpression?.valueArguments
                else -> null
            } ?: return@forEach
            valueArguments[argumentIndex]?.getArgumentExpression()?.replace(branchExpression)
            branchExpression.replace(copied)
        }
        qualifiedOrCall.replace(argument).reformatted()
    }

    inline fun <reified T: KtExpression> KtCallExpression.argumentOfType(): Pair<T, Int>? =
        valueArguments.mapIndexedNotNull { index, argument ->
            val expression = argument.getArgumentExpression() as? T ?: return@mapIndexedNotNull null
            expression to index
        }.singleOrNull()

    fun KtExpression?.singleExpression(): KtExpression? =
        if (this is KtBlockExpression) this.statements.singleOrNull() else this
}
