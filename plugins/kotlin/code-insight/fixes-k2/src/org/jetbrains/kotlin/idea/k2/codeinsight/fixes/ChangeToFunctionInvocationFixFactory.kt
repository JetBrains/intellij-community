// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
import org.jetbrains.kotlin.psi.KtExpression

internal object ChangeToFunctionInvocationFixFactory {

    val changeToFunctionInvocationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.FunctionCallExpected ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()

        listOf(
            ChangeToFunctionInvocationFix(expression)
        )
    }
}