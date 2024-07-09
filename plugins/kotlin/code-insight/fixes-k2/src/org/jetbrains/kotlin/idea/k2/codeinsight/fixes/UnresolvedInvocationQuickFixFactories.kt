// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix

object UnresolvedInvocationQuickFixFactories {

    val changeToPropertyAccessQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FunctionExpected ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableParentCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.ChangeToPropertyAccessQuickFix(expression))
    }

    val removeParentInvocationQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableParentCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.RemoveInvocationQuickFix(expression))
    }

    val removeInvocationQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        val expression = UnresolvedInvocationQuickFix.findAcceptableCallExpression(diagnostic.psi)
            ?: return@ModCommandBased emptyList()

        listOf(UnresolvedInvocationQuickFix.RemoveInvocationQuickFix(expression))
    }
}