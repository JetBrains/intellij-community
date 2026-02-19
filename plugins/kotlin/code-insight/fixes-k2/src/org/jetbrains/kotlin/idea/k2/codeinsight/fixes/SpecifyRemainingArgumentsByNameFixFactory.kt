// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingArgumentsByNameFix
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

internal object SpecifyRemainingArgumentsByNameFixFactory {

    private fun KaSession.createFixes(psiElement: PsiElement): List<SpecifyRemainingArgumentsByNameFix> {
        val callExpression = psiElement as? KtCallExpression ?: return emptyList()
        val argumentList = callExpression.valueArgumentList ?: return emptyList()
        val remainingArguments = findRemainingNamedArguments(argumentList) ?: return emptyList()
        return SpecifyRemainingArgumentsByNameFix.createAvailableQuickFixes(argumentList, remainingArguments)
    }

    val noValueForParameter =  KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        val psiElement = diagnostic.psi
        val psiToCheck = if (psiElement is KtDotQualifiedExpression) {
            psiElement.selectorExpression ?: return@ModCommandBased emptyList()
        } else {
            psiElement
        }
        createFixes(psiToCheck)
    }

    val noneApplicable = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoneApplicable ->
        createFixes(diagnostic.psi.parent)
    }
}