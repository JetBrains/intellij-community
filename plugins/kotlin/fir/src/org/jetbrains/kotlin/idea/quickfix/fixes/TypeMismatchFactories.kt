// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object TypeMismatchFactories {
    val argumentTypeMismatchFactory = diagnosticFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class) { diagnostic ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatchFactory = diagnosticFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class) { diagnostic ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.AssignmentTypeMismatch::class) { diagnostic ->
        getFixesForTypeMismatch(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch = diagnosticFixFactory(KtFirDiagnostic.InitializerTypeMismatch::class) { diagnostic ->
        diagnostic.psi.initializer?.let { getFixesForTypeMismatch(it, diagnostic.expectedType, diagnostic.actualType) } ?: emptyList()
    }

    val smartcastImpossibleFactory = diagnosticFixFactory(KtFirDiagnostic.SmartcastImpossible::class) { diagnostic ->
        val psi = diagnostic.psi
        val actualType = psi.getKtType() ?: return@diagnosticFixFactory emptyList()
        getFixesForTypeMismatch(psi, expectedType = diagnostic.desiredType, actualType = actualType)
    }

    private fun KtAnalysisSession.getFixesForTypeMismatch(
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
            if (actualType isSubTypeOf nullableExpectedType) {
                return listOfNotNull(psi.asAddExclExclCallFix())
            }
        }
        return emptyList()
    }
}
