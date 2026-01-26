// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix.CollectionType
import org.jetbrains.kotlin.psi.KtCallExpression
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


    private fun KaSession.createFixIfAvailable(
        element: PsiElement,
        expectedType: KaType,
        actualType: KaType,
    ): List<ModCommandAction> {
        val expression = element as? KtExpression ?: return emptyList()

        // Try to create a fix for replacing immutable collection factory calls (e.g., listOf, emptySet)
        // with their mutable equivalents (e.g., mutableListOf, mutableSetOf). If that's not applicable,
        // fall back to creating a fix that adds a conversion function (e.g., toList, toMutableSet) to
        // convert between incompatible collection types.
        val fix = createReplaceWithMutableCollectionFactoryFix(expression, expectedType)
            ?: createConvertCollectionFix(expression, expectedType, actualType)
        return listOfNotNull(fix)
    }

    private fun KaSession.createReplaceWithMutableCollectionFactoryFix(
        expression: KtExpression,
        expectedType: KaType,
    ): ModCommandAction? {
        if (expression !is KtCallExpression) return null

        val symbol = expression.resolveExpression() as? KaNamedFunctionSymbol ?: return null
        val expectedCollectionType = when (symbol.importableFqName) {
            StandardKotlinNames.Collections.emptyList -> CollectionType.MutableList
            StandardKotlinNames.Collections.emptyMap -> CollectionType.MutableMap
            StandardKotlinNames.Collections.emptySet -> CollectionType.MutableSet
            StandardKotlinNames.Collections.listOf -> CollectionType.MutableList
            StandardKotlinNames.Collections.mapOf -> CollectionType.MutableMap
            StandardKotlinNames.Collections.setOf -> CollectionType.MutableSet
            else -> return null
        }

        val literalFunctionName = expectedCollectionType.literalFunctionName ?: return null
        if (getCollectionType(expectedType) != expectedCollectionType) return null

        return ReplaceWithMutableCollectionFactoryFix(expression, literalFunctionName)
    }

    private fun KaSession.createConvertCollectionFix(
        element: KtExpression,
        expectedType: KaType,
        actualType: KaType,
    ): ModCommandAction? {
        val collectionType = getConversionTypeOrNull(actualType, expectedType) ?: return null
        return ConvertCollectionFix(element, collectionType)
    }

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val typeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
        createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
    }

    val incompatibleTypes = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IncompatibleTypes ->
        createFixIfAvailable(diagnostic.psi, diagnostic.typeA, diagnostic.typeB)
    }

    val initializerTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        val initializer = diagnostic.initializer ?: return@ModCommandBased emptyList()
        createFixIfAvailable(initializer, diagnostic.expectedType, diagnostic.actualType)
    }

    val assignmentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        createFixIfAvailable(diagnostic.expression, diagnostic.expectedType, diagnostic.actualType)
    }
}