// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.psi.KtCallExpression

object SpecifyRemainingArgumentsByNameFixFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val functionCallPsi = if (diagnostic.factory == Errors.NONE_APPLICABLE) {
            diagnostic.psiElement.parent
        } else diagnostic.psiElement.parent

        if (functionCallPsi !is KtCallExpression) return emptyList()

        val argumentList = functionCallPsi.valueArgumentList ?: return emptyList()
        return analyze(argumentList) {
            val remainingArguments = findRemainingNamedArguments(argumentList) ?: return emptyList()
            SpecifyRemainingArgumentsByNameFix.createAvailableQuickFixes(argumentList, remainingArguments).map { it.asIntention() }
        }
    }
}