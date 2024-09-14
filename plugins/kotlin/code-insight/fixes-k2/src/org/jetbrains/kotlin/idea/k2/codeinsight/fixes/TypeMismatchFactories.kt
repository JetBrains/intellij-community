// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object TypeMismatchFactories {

    val argumentTypeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        (diagnostic.psi as? KtProperty)?.initializer?.let { getFixesForTypeMismatch(it, diagnostic.expectedType, diagnostic.actualType) }
            ?: emptyList()
    }

    val smartcastImpossibleFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.SmartcastImpossible ->
        val psi = diagnostic.psi
        val actualType = psi.expressionType
            ?: return@IntentionBased emptyList()

        getFixesForTypeMismatch(psi, expectedType = diagnostic.desiredType, actualType = actualType)
    }

    val conditionTypeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ConditionTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, expectedType = builtinTypes.boolean, actualType = diagnostic.actualType)
    }

    private fun KaSession.getFixesForTypeMismatch(
        psi: PsiElement,
        expectedType: KaType,
        actualType: KaType
    ): List<IntentionAction> {
        // TODO: Add more fixes than just AddExclExclCallFix when available.
        if (!expectedType.canBeNull && actualType.canBeNull) {
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
                        add(AddEqEqTrueFix(psi).asIntention())
                    }
                }
            }
        }
        return emptyList()
    }
}
