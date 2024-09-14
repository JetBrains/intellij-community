// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddToStringFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

object AddToStringFixFactories {

    private fun KaSession.getFixes(element: PsiElement?, expectedType: KaType, actualType: KaType): List<AddToStringFix> {
        if (element !is KtExpression) return emptyList()
        return buildList {
            if (expectedType.isStringType || expectedType.isCharSequenceType) {
                add(AddToStringFix(element, false))
                if (expectedType.isMarkedNullable && actualType.isMarkedNullable) {
                    add(AddToStringFix(element, true))
                }
            }
        }
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        getFixes((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType, diagnostic.actualType)
    }
}