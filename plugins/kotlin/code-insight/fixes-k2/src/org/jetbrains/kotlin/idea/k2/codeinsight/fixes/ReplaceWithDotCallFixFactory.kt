// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ReplaceWithDotCallFixFactory {
    val replaceWithDotCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnnecessarySafeCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnnecessarySafeCall ->
            val qualifiedExpression = diagnostic.psi.getParentOfType<KtSafeQualifiedExpression>(strict = false)
                ?: return@ModCommandBased emptyList()

            listOf(
                ReplaceWithDotCallFix(qualifiedExpression, qualifiedExpression.getSafeCallChainCount()),
            )
        }

    context(_: KaSession)
    private fun KtSafeQualifiedExpression.getSafeCallChainCount(): Int {
        val unnecessarySafeCalls = containingKtFile
            .collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            .filterIsInstance<KaFirDiagnostic.UnnecessarySafeCall>()
            .mapNotNullTo(hashSetOf()) { it.psi.getParentOfType<KtSafeQualifiedExpression>(strict = false) }

        var expression = this
        var callChainCount = 0
        while (true) {
            val parent = expression.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression ?: break
            if (parent !in unnecessarySafeCalls && !expression.selectorHasNotNullReturnType()) break
            expression = parent
            callChainCount++
        }
        return callChainCount
    }

    context(session: KaSession)
    private fun KtSafeQualifiedExpression.selectorHasNotNullReturnType(): Boolean {
        val returnType = resolveToCall()
            ?.singleCallOrNull<KaCallableMemberCall<*, *>>()
            ?.partiallyAppliedSymbol
            ?.signature
            ?.returnType
        return returnType?.isMarkedNullable == false
    }
}
