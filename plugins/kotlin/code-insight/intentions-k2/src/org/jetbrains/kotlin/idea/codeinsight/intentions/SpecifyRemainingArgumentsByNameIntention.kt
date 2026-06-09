// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal abstract class SpecifyRemainingArgumentsByNameIntention :
    KotlinApplicableModCommandAction<KtElement, RemainingArgumentsData>(KtElement::class) {

    override fun isApplicableByPsi(element: KtElement): Boolean {
        return element is KtCallExpression || element is KtValueArgumentList
    }

    abstract fun shouldShowFor(element: KtElement, remainingArgumentsData: RemainingArgumentsData): Boolean

    override fun getApplicableRanges(element: KtElement): List<TextRange> {
        return when (element) {
            is KtCallExpression -> {
                if (element.valueArgumentList == null) return emptyList()
                ApplicabilityRanges.calleeExpression(element)
            }

            is KtValueArgumentList -> {
                val firstArgument = element.arguments.firstOrNull() ?: return ApplicabilityRange.self(element)
                val lastArgument = element.arguments.lastOrNull() ?: firstArgument
                val startTextRange = TextRange(0, firstArgument.startOffsetInParent)
                val endTextRange = TextRange(lastArgument.startOffsetInParent + lastArgument.textLength, element.textLength)

                listOf(startTextRange, endTextRange)
            }

            else -> emptyList()
        }
    }

    override fun KaSession.prepareContext(element: KtElement): RemainingArgumentsData? {
        val argumentList = element.getValueArgumentList() ?: return null
        return findRemainingNamedArguments(argumentList)?.takeIf { shouldShowFor(element, it) }
    }

    protected fun KtElement.getValueArgumentList(): KtValueArgumentList? = when (this) {
        is KtValueArgumentList -> this
        is KtCallExpression -> this.valueArgumentList
        else -> null
    }
}
