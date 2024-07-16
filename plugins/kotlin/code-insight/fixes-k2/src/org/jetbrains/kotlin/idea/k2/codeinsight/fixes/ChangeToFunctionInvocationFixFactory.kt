// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments

internal object ChangeToFunctionInvocationFixFactory {

    val changeToFunctionInvocationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FunctionCallExpected ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        val next = expression.getNextSiblingIgnoringWhitespaceAndComments()
        val parent = expression.parent
        if (next is KtTypeArgumentList && parent is KtCallExpression) {
            return@ModCommandBased listOf(ChangeToFunctionInvocationFix(parent))
        }
        listOf(ChangeToFunctionInvocationFix(expression))
    }
}