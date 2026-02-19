// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFix
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object UselessIsCheckFactories {
    val uselessIsCheckFactory =
        prepareRemoveUselessIsCheckFix<KaFirDiagnostic.UselessIsCheck> { compileTimeCheckResult }

    val impossibleIsCheckWarningFactory =
        prepareRemoveUselessIsCheckFix<KaFirDiagnostic.ImpossibleIsCheckWarning> { compileTimeCheckResult }

    val impossibleIsCheckErrorFactory =
        prepareRemoveUselessIsCheckFix<KaFirDiagnostic.ImpossibleIsCheckError> { compileTimeCheckResult }

    private inline fun <T: KaFirDiagnostic<KtElement>> prepareRemoveUselessIsCheckFix(crossinline compileTimeCheckResult: T.() -> Boolean) =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: T ->
            val element = diagnostic.psi.takeIf { it.isWritable } ?: return@ModCommandBased emptyList()
            val expression = element.getNonStrictParentOfType<KtIsExpression>() ?: return@ModCommandBased emptyList()
            listOf(RemoveUselessIsCheckFix(expression, diagnostic.compileTimeCheckResult()))
        }

    val uselessWhenCheckFactory =
        prepareRemoveUselessIsCheckFixForWhen<KaFirDiagnostic.UselessIsCheck> { compileTimeCheckResult }

    val impossibleWhenCheckWarningFactory =
        prepareRemoveUselessIsCheckFixForWhen<KaFirDiagnostic.ImpossibleIsCheckWarning> { compileTimeCheckResult }

    val impossibleWhenCheckErrorFactory =
        prepareRemoveUselessIsCheckFixForWhen<KaFirDiagnostic.ImpossibleIsCheckError> { compileTimeCheckResult }

    private inline fun <T: KaFirDiagnostic<KtElement>> prepareRemoveUselessIsCheckFixForWhen(crossinline compileTimeCheckResult: T.() -> Boolean) =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: T ->
            val element = diagnostic.psi.takeIf { it.isWritable } ?: return@ModCommandBased emptyList()
            val expression = element.getNonStrictParentOfType<KtWhenConditionIsPattern>() ?: return@ModCommandBased emptyList()
            if (expression.getStrictParentOfType<KtWhenEntry>()?.guard != null) return@ModCommandBased emptyList()
            listOf(RemoveUselessIsCheckFixForWhen(expression, diagnostic.compileTimeCheckResult()))
        }

}
