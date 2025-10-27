// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

internal object WrapWithCollectionLiteralCallFixFactory {
    private fun KaSession.createIfAvailable(element: PsiElement, expectedType: KaType, actualType: KaType): List<ModCommandAction> {
        val expression = element as? KtExpression ?: return emptyList()
        if (expression.getStrictParentOfType<KtAnnotationEntry>() != null) return emptyList()

        val collectionType = ConvertCollectionFixFactory.getCollectionType(expectedType) ?: return emptyList()

        val expectedArgumentType =
            (expectedType as? KaClassType)
                ?.typeArguments?.singleOrNull()
                ?.takeIf { (it as? KaTypeArgumentWithVariance)?.variance != Variance.IN_VARIANCE }
                ?.type
                ?: return emptyList()

        val result = mutableListOf<WrapWithCollectionLiteralCallFix>()

        val isNullExpression = expression.isNullExpression()
        val literalFunctionName = collectionType.literalFunctionName
        if ((actualType.isSubtypeOf(expectedArgumentType) || isNullExpression) && literalFunctionName != null) {
            result += WrapWithCollectionLiteralCallFix(expression, literalFunctionName, wrapInitialElement = true)
        }

        // Replace "null" with emptyList()
        val emptyCollectionFunction = collectionType.emptyCollectionFunction
        if (isNullExpression && emptyCollectionFunction != null) {
            result += WrapWithCollectionLiteralCallFix(expression, emptyCollectionFunction, wrapInitialElement = false)
        }

        return result
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val incompatibleTypes = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IncompatibleTypes ->
        createIfAvailable(diagnostic.psi, diagnostic.typeA, diagnostic.typeB)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        createIfAvailable(diagnostic.expression, diagnostic.expectedType, diagnostic.actualType)
    }

    val nullForNonNullType = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.expectedType.withNullability(KaTypeNullability.NULLABLE))
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        val initializer = diagnostic.initializer ?: return@ModCommandBased emptyList()
        createIfAvailable(initializer, diagnostic.expectedType, diagnostic.actualType)
    }
}