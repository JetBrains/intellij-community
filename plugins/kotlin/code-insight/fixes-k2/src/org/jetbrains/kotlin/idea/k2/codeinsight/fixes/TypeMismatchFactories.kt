// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object TypeMismatchFactories {

    val argumentTypeMismatchFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.ArgumentTypeMismatch> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatchFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.ReturnTypeMismatch> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.AssignmentTypeMismatch> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.expression, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.InitializerTypeMismatch> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        diagnostic.initializer?.let { getFixesForTypeMismatch(it, diagnostic.expectedType, diagnostic.actualType) }
            ?: emptyList()
    }

    val smartcastImpossibleFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.SmartcastImpossible> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SmartcastImpossible ->
        val psi = diagnostic.psi
        val actualType = psi.expressionType
            ?: return@ModCommandBased emptyList()

        getFixesForTypeMismatch(psi, expectedType = diagnostic.desiredType, actualType = actualType)
    }

    val conditionTypeMismatchFactory: KotlinQuickFixFactory<KaFirDiagnostic.ConditionTypeMismatch> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ConditionTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, expectedType = builtinTypes.boolean, actualType = diagnostic.actualType)
    }

    private fun KaSession.getFixesForTypeMismatch(
        psi: PsiElement,
        expectedType: KaType,
        actualType: KaType
    ): List<ModCommandAction> {
        // TODO: Add more fixes than just AddExclExclCallFix when available.
        if (!expectedType.isNullable && actualType.isNullable) {
            // We don't want to offer AddExclExclCallFix if we know the expression is definitely null, e.g.:
            //
            //   if (nullableInt == null) {
            //     val x: Int = nullableInt // No AddExclExclCallFix here
            //   }
            if (psi.safeAs<KtExpression>()?.isDefinitelyNull == true) {
                return emptyList()
            }
            val nullableExpectedType = expectedType.withNullability(KaTypeNullability.NULLABLE)
            if (actualType.isSubtypeOf(nullableExpectedType)) {
                return buildList {
                    psi.asAddExclExclCallFix()?.let(::add)
                    if (expectedType.isBooleanType && psi is KtExpression) {
                        add(AddEqEqTrueFix(psi))
                    }
                }
            }
        }
        return emptyList()
    }
}
