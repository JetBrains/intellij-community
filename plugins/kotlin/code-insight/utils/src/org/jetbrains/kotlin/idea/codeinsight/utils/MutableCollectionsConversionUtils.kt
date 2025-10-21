// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

/**
 * Utils to convert code from using immutable collections (`List`, `Set`, `Map`) to
 * their mutable counterparts.
 */
object MutableCollectionsConversionUtils {

    /**
     * N.B. This check intentionally ignores `Set` type, because there are no
     * `set` operator on a `Set` - hence, no `NO_SET_METHOD` diagnostic ever reported.
     */
    fun isReadOnlyListOrMap(classId: ClassId): Boolean =
        classId == StandardClassIds.List
                || classId == StandardClassIds.Map

    fun canConvertPropertyType(property: KtProperty): Boolean {
        return property.isLocal && property.initializer != null
    }

    fun defaultValue(declaration: KtCallableDeclaration): KtExpression? = when (declaration) {
        is KtDeclarationWithInitializer -> declaration.initializer
        is KtParameter -> declaration.defaultValue
        else -> null
    }

    private sealed interface MutableCollectionCall

    private data class TopLevelCall(
        val callableId: CallableId,
        val replacement: String,
    ) : MutableCollectionCall

    private data class ConstructorCall(
        val classId: ClassId,
    ) : MutableCollectionCall

    private fun callableName(
        initializer: KtExpression,
    ): MutableCollectionCall? = analyze(initializer) {
        val functionSymbol = initializer.resolveToCall()
            ?.singleFunctionCallOrNull()
            ?.symbol
            ?: return@analyze null

        when (functionSymbol) {
            is KaConstructorSymbol -> {
                val containingClassId = functionSymbol.containingClassId
                    ?: return@analyze null

                val returnType = functionSymbol.returnType
                val hasMutableCollectionType = returnType.isSubtypeOf(StandardClassIds.MutableList)
                        || returnType.isSubtypeOf(StandardClassIds.MutableSet)
                        || returnType.isSubtypeOf(StandardClassIds.MutableMap)
                if (!hasMutableCollectionType) return@analyze null

                ConstructorCall(containingClassId)
            }

            else -> {
                val callableId = functionSymbol.callableId
                    ?: return@analyze null

                val replacement = callableId.asSingleFqName()
                    .asString()
                    .let(::convertToMutable)
                    ?: return@analyze null

                TopLevelCall(callableId, replacement)
            }
        }
    }

    fun convertDeclarationTypeToMutable(
        declaration: KtCallableDeclaration,
        immutableCollectionClassId: ClassId,
        psiFactory: KtPsiFactory = KtPsiFactory(declaration.project),
    ) {
        val defaultValue = defaultValue(declaration) ?: return

        when (val callableName = callableName(defaultValue)) {
            is TopLevelCall -> {
                (defaultValue as? KtCallExpression)
                    ?.calleeExpression
                    ?.replaced(psiFactory.createExpression(callableName.replacement))
                    ?: return
            }

            is ConstructorCall -> {}

            else -> {
                val toMutable = toMutableCollectionCallableName(immutableCollectionClassId) ?: return
                val dotQualifiedExpression = defaultValue.replaced(
                    psiFactory.createExpressionByPattern("($0).$1()", defaultValue, toMutable)
                ) as KtDotQualifiedExpression
                val receiver = dotQualifiedExpression.receiverExpression
                val deparenthesize = KtPsiUtil.deparenthesize(dotQualifiedExpression.receiverExpression)
                if (deparenthesize != null && receiver != deparenthesize) {
                    receiver.replace(deparenthesize)
                }
            }
        }

        declaration.typeReference?.let { typeReference ->
            typeReference.replace(psiFactory.createType("Mutable${typeReference.text}"))
        }
    }

    fun toMutableCollectionCallableName(immutableCollectionClassId: ClassId): @NonNls String? =
        when (immutableCollectionClassId) {
            StandardClassIds.List -> "toMutableList"
            StandardClassIds.Set -> "toMutableSet"
            StandardClassIds.Map -> "toMutableMap"
            else -> null
        }

    private const val COLLECTIONS: String = "kotlin.collections"

    private fun convertToMutable(text: String): String? = when (text) {
        "$COLLECTIONS.listOf",
        "$COLLECTIONS.emptyList" -> "mutableListOf"

        "$COLLECTIONS.setOf",
        "$COLLECTIONS.emptySet" -> "mutableSetOf"

        "$COLLECTIONS.mapOf",
        "$COLLECTIONS.emptyMap" -> "mutableMapOf"

        else -> null
    }
}