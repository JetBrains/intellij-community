// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object NoReturnInFunctionWithBlockBodyFixFactory {

    val addReturnToLastExpression = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoReturnInFunctionWithBlockBody ->
        val namedFunction = diagnostic.psi as? KtNamedFunction ?: return@ModCommandBased emptyList()
        val last = namedFunction.bodyBlockExpression?.statements?.lastOrNull() ?: return@ModCommandBased emptyList()
        val lastType = last.expressionType?.takeIf { it !is KaErrorType } ?: return@ModCommandBased emptyList()
        val expectedType = namedFunction.returnType.takeIf { it !is KaErrorType } ?: return@ModCommandBased emptyList()
        if (!lastType.isSubtypeOf(expectedType)) return@ModCommandBased emptyList()

        listOf(
            AddReturnToLastExpressionInFunctionFix(namedFunction)
        )
    }
}
