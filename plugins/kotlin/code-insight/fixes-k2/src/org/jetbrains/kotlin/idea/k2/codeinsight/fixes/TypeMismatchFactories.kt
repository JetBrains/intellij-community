// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object TypeMismatchFactories {

    val argumentTypeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ReturnTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.AssignmentTypeMismatch ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.InitializerTypeMismatch ->
        (diagnostic.psi as? KtProperty)?.initializer?.let { getFixesForTypeMismatch(it, diagnostic.expectedType, diagnostic.actualType) }
            ?: emptyList()
    }

    val smartcastImpossibleFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.SmartcastImpossible ->
        val psi = diagnostic.psi
        val actualType = psi.getKtType()
            ?: return@IntentionBased emptyList()

        getFixesForTypeMismatch(psi, expectedType = diagnostic.desiredType, actualType = actualType)
    }

    context(KtAnalysisSession)
    private fun getFixesForTypeMismatch(
        psi: PsiElement,
        expectedType: KtType,
        actualType: KtType
    ): List<AddExclExclCallFix> {
        // TODO: Add more fixes than just AddExclExclCallFix when available.
        if (!expectedType.canBeNull && actualType.canBeNull) {
            // We don't want to offer AddExclExclCallFix if we know the expression is definitely null, e.g.:
            //
            //   if (nullableInt == null) {
            //     val x: Int = nullableInt  // No AddExclExclCallFix here
            //   }
            if (psi.safeAs<KtExpression>()?.isDefinitelyNull() == true) {
                return emptyList()
            }
            val nullableExpectedType = expectedType.withNullability(KtTypeNullability.NULLABLE)
            if (actualType.isSubTypeOf(nullableExpectedType)) {
                return listOfNotNull(psi.asAddExclExclCallFix())
            }
        }
        return emptyList()
    }
}
