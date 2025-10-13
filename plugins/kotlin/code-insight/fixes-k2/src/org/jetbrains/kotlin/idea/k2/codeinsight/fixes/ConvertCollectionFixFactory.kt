// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix.CollectionType
import org.jetbrains.kotlin.psi.KtExpression

internal object ConvertCollectionFixFactory {
    internal fun getCollectionType(type: KaType, acceptNullableTypes: Boolean = false): CollectionType? {
        if (type.nullability.isNullable && !acceptNullableTypes) return null
        return CollectionType.entries.firstOrNull {
            type.symbol?.classId?.asSingleFqName() == it.fqName
        }
    }

    private fun KaSession.getConversionTypeOrNull(expressionType: KaType, expectedType: KaType): CollectionType? {
        val expressionCollectionType = getCollectionType(expressionType) ?: return null
        val expectedCollectionType = getCollectionType(expectedType) ?: return null
        if (expressionCollectionType == expectedCollectionType) return null

        val expressionTypeArg = (expressionType as? KaClassType)?.typeArguments?.singleOrNull()?.type ?: return null
        val expectedTypeArg = (expectedType as? KaClassType)?.typeArguments?.singleOrNull()?.type ?: return null

        if (!expressionTypeArg.isSubtypeOf(expectedTypeArg)) return null

        return expectedCollectionType.specializeFor(expressionCollectionType)
    }


    private fun KaSession.createIfAvailable(element: PsiElement, expectedType: KaType, actualType: KaType): List<ModCommandAction> {
        val expression = element as? KtExpression ?: return emptyList()
        val collectionType = getConversionTypeOrNull(actualType, expectedType) ?: return emptyList()

        return listOf(ConvertCollectionFix(expression, collectionType))
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
        createIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val incompatibleTypes = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IncompatibleTypes ->
        createIfAvailable(diagnostic.psi, diagnostic.typeA, diagnostic.typeB)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        val initializer = diagnostic.initializer ?: return@ModCommandBased emptyList()
        createIfAvailable(initializer, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        createIfAvailable(diagnostic.expression, diagnostic.expectedType, diagnostic.actualType)
    }
}