// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
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

    private fun mutableCallableName(
        initializer: KtExpression,
    ): String? {
        val fqName = analyze(initializer) {
            initializer.resolveToCall()
                ?.singleFunctionCallOrNull()
                ?.symbol
                ?.callableId
        }?.asSingleFqName()

        return mutableConversionMap[fqName?.asString()]
    }

    fun convertPropertyTypeToMutable(
        property: KtDeclarationWithInitializer,
        immutableCollectionClassId: ClassId,
        psiFactory: KtPsiFactory = KtPsiFactory(property.project),
    ) {
        val initializer = property.initializer ?: return

        val mutableCallableName = mutableCallableName(initializer)
        if (mutableCallableName != null) {
            (initializer as? KtCallExpression)
                ?.calleeExpression
                ?.replaced(psiFactory.createExpression(mutableCallableName))
                ?: return
        } else {
            val toMutable = when (immutableCollectionClassId) {
                StandardClassIds.List -> "toMutableList"
                StandardClassIds.Set -> "toMutableSet"
                StandardClassIds.Map -> "toMutableMap"
                else -> null
            } ?: return
            val dotQualifiedExpression = initializer.replaced(
                psiFactory.createExpressionByPattern("($0).$1()", initializer, toMutable)
            ) as KtDotQualifiedExpression
            val receiver = dotQualifiedExpression.receiverExpression
            val deparenthesize = KtPsiUtil.deparenthesize(dotQualifiedExpression.receiverExpression)
            if (deparenthesize != null && receiver != deparenthesize) {
                receiver.replace(deparenthesize)
            }
        }
        property.typeReference?.let { typeReference ->
            typeReference.replace(psiFactory.createType("Mutable${typeReference.text}"))
        }
    }

    private const val COLLECTIONS: String = "kotlin.collections"

    private val mutableConversionMap: Map<String, String> = mapOf(
        "$COLLECTIONS.listOf" to "mutableListOf",
        "$COLLECTIONS.setOf" to "mutableSetOf",
        "$COLLECTIONS.mapOf" to "mutableMapOf"
    )
}