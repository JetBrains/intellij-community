// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class SpecifyAllRemainingArgumentsByNameIntention: KotlinApplicableModCommandAction<KtValueArgumentList, RemainingArgumentsData>(KtValueArgumentList::class) {
    override fun getFamilyName(): String = KotlinBundle.message("specify.all.remaining.arguments.by.name")

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
        elementContext: RemainingArgumentsData,
        updater: ModPsiUpdater
    ) {
        SpecifyRemainingArgumentsByNameUtil.applyFix(actionContext.project, element, elementContext.allRemainingArguments, updater)
    }

    override fun KaSession.prepareContext(element: KtValueArgumentList): RemainingArgumentsData? {
        val remainingArguments = findRemainingNamedArguments(element) ?: return null

        return remainingArguments
    }
}