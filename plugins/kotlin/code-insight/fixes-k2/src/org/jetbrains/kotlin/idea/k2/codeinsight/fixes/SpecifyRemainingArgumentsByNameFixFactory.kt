// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.findRemainingNamedArguments
import org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingArgumentsByNameFix
import org.jetbrains.kotlin.psi.KtCallExpression

internal object SpecifyRemainingArgumentsByNameFixFactory {
    val noValueForParameter =  KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        val callExpression = diagnostic.psi as? KtCallExpression ?: return@ModCommandBased emptyList()
        val argumentList = callExpression.valueArgumentList ?: return@ModCommandBased emptyList()
        val remainingArguments = findRemainingNamedArguments(argumentList) ?: return@ModCommandBased emptyList()
        SpecifyRemainingArgumentsByNameFix.createAvailableQuickFixes(argumentList, remainingArguments)
    }
}