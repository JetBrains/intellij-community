// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

object Fe10ReplaceWithDotCallFixFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
    override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
        val qualifiedExpression = psiElement.getParentOfType<KtSafeQualifiedExpression>(strict = false)
            ?: return emptyList()

        var parent = qualifiedExpression.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
        var callChainCount = 0
        if (parent != null) {
            val bindingContext = qualifiedExpression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
            while (parent is KtQualifiedExpression) {
                val compilerReports = bindingContext.diagnostics.forElement(parent.operationTokenNode as PsiElement)
                if (compilerReports.none { it.factory == Errors.UNNECESSARY_SAFE_CALL }) break
                callChainCount++
                parent = parent.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
            }
        }

        return listOf(
            ReplaceWithDotCallFix(qualifiedExpression, callChainCount).asIntention()
        )
    }
}