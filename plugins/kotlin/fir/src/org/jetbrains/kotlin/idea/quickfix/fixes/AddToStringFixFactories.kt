// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddToStringFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

object AddToStringFixFactories {
    context(KtAnalysisSession)
    private fun getFixes(element: PsiElement?, expectedType: KtType, actualType: KtType): List<AddToStringFix> {
        if (element !is KtExpression) return emptyList()
        return buildList {
            if (expectedType.isString || expectedType.isCharSequence) {
                add(AddToStringFix(element, false))
                if (expectedType.isMarkedNullable && actualType.isMarkedNullable) {
                    add(AddToStringFix(element, true))
                }
            }
        }
    }

    val typeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.TypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.TypeMismatch::class) { diagnostic ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val argumentTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.ArgumentTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class) { diagnostic ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.AssignmentTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.AssignmentTypeMismatch::class) { diagnostic ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.ReturnTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class) { diagnostic ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch: KotlinDiagnosticModCommandFixFactory<KtFirDiagnostic.InitializerTypeMismatch> = diagnosticModCommandFixFactory(KtFirDiagnostic.InitializerTypeMismatch::class) { diagnostic ->
        getFixes((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType, diagnostic.actualType)
    }
}