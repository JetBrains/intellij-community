// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingNamedArgumentData
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal sealed class SpecifyRemainingArgumentsByNameIntention(
    @Nls
    private val familyName: String
) : KotlinApplicableModCommandAction<KtValueArgumentList, List<RemainingNamedArgumentData>>(KtValueArgumentList::class) {
    override fun getFamilyName(): String = familyName

    /**
     * Returns whether the [argument] should be specified by this intention.
     * The intention is only shown if at least one argument satisfies this function.
     */
    internal abstract fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean

    /**
     * Return true if the intention should be offered to the user.
     * This is used to hide implementations of this class if another one with the same effect is already shown.
     */
    internal abstract fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean

    override fun getApplicableRanges(element: KtValueArgumentList): List<TextRange> {
        val firstArgument = element.arguments.firstOrNull() ?: return ApplicabilityRange.self(element)
        val lastArgument = element.arguments.lastOrNull() ?: firstArgument

        // We only want the intention to show if the caret is near the start or end of the argument list
        val startTextRange = TextRange(0, firstArgument.startOffsetInParent)
        val endTextRange = TextRange(lastArgument.startOffsetInParent + lastArgument.textLength, element.textLength)

        return listOf(startTextRange, endTextRange)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgumentList,
        elementContext: List<RemainingNamedArgumentData>,
        updater: ModPsiUpdater
    ) {
        SpecifyRemainingArgumentsByNameUtil.applyFix(actionContext.project, element, elementContext, updater)
    }

    context(KaSession)
    override fun prepareContext(element: KtValueArgumentList): List<RemainingNamedArgumentData>? {
        val remainingArguments = findRemainingNamedArguments(element) ?: return null
        if (!shouldOfferIntention(remainingArguments)) return null

        return remainingArguments
            .filter { shouldSpecifyArgument(it) }
            .takeIf { it.isNotEmpty() }
    }
}

internal class SpecifyAllRemainingArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention(
    KotlinBundle.getMessage("specify.all.remaining.arguments.by.name")
) {
    override fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean = true
    override fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean = true
}

internal class SpecifyRemainingRequiredArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention(
    KotlinBundle.getMessage("specify.remaining.required.arguments.by.name")
) {
    override fun shouldSpecifyArgument(argument: RemainingNamedArgumentData): Boolean = !argument.hasDefault

    override fun shouldOfferIntention(remainingArguments: List<RemainingNamedArgumentData>): Boolean {
        val argumentsWithDefault = remainingArguments.count { it.hasDefault }
        return argumentsWithDefault in (1..<remainingArguments.size)
    }
}