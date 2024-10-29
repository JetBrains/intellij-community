// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

internal object ConvertKClassToClassFixFactories {

    val argumentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val returnTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val initializerTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val assignmentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    private fun KaSession.createFixIfAvailable(
        element: PsiElement?,
        expectedType: KaType,
        actualType: KaType,
    ): ConvertKClassToClassFix? {
        if (element !is KtExpression) return null
        if (!isKClass(actualType) || !isJavaClass(expectedType)) return null
        val codeFragment = KtPsiFactory(element.project).createExpressionCodeFragment(element.text + ".java", element)
        val contentElement = codeFragment.getContentElement() ?: return null
        val javaLangClassType = contentElement.expressionType ?: return null
        if (!javaLangClassType.isSubtypeOf(expectedType)) return null
        return ConvertKClassToClassFix(element)
    }
}
