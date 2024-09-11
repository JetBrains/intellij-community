// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

internal object ConvertClassToKClassFixFactories {

    val argumentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        listOfNotNull(createFixIfAvailable(diagnostic.psi, diagnostic.expectedType))
    }

    val returnTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        listOfNotNull(createFixIfAvailable(diagnostic.psi, diagnostic.expectedType))
    }

    val initializerTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        listOfNotNull(createFixIfAvailable((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType))
    }

    val assignmentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        listOfNotNull(createFixIfAvailable(diagnostic.psi, diagnostic.expectedType))
    }

    context(KaSession)
    private fun createFixIfAvailable(element: PsiElement?, expectedType: KaType): ConvertClassToKClassFix? {
        val dotQualifiedExpression = element as? KtDotQualifiedExpression ?: return null
        if (!expectedType.isKClass()) return null

        val expressionType = dotQualifiedExpression.expressionType ?: return null
        if (!expressionType.isJavaClass()) return null

        val children = dotQualifiedExpression.children
        if (children.size != 2) return null

        val firstChild = children.first() as? KtExpression ?: return null
        val firstChildType = firstChild.expressionType ?: return null

        if (!firstChildType.isSubtypeOf(expectedType)) return null

        return ConvertClassToKClassFix(dotQualifiedExpression)
    }

    context(KaSession)
    private fun KaType.isKClass(): Boolean = isClassType(StandardClassIds.KClass)

    context(KaSession)
    private fun KaType.isJavaClass(): Boolean = isClassType(ClassId.fromString("java/lang/Class"))
}
