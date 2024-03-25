// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
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

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.TypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.AssignmentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ReturnTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.InitializerTypeMismatch ->
        getFixes((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType, diagnostic.actualType)
    }
}