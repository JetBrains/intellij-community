// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.createExpressionByPattern
import kotlin.collections.get

/**
 * Utils to convert code from using immutable collections (`List`, `Set`, `Map`) to
 * their mutable counterparts.
 */
object MutableCollectionsConversionUtils {

    /**
     * N.B. This check intentionally ignores `Set` type, because there are no
     * `set` operator on a `Set` - hence, no `NO_SET_METHOD` diagnostic ever reported.
     */
    fun KaSession.isReadOnlyListOrMap(type: KaClassType): Boolean {
        return type.classId in listOf(
            StandardClassIds.List,
            StandardClassIds.Map,
        )
    }

    fun canConvertPropertyType(property: KtProperty): Boolean {
        return property.isLocal && property.initializer != null
    }

    fun KaSession.convertPropertyTypeToMutable(property: KtProperty, type: ClassId) {
        val initializer = property.initializer ?: return
        val fqName = initializer.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString()
        val mutableOf = mutableConversionMap[fqName]
        val psiFactory = KtPsiFactory(property.project)
        if (mutableOf != null) {
            (initializer as? KtCallExpression)?.calleeExpression?.replaced(psiFactory.createExpression(mutableOf)) ?: return
        } else {
            val toMutable = when (type) {
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
        property.typeReference?.also { it.replace(psiFactory.createType("Mutable${it.text}")) }
    }

    private const val COLLECTIONS: String = "kotlin.collections"

    private val mutableConversionMap: Map<String, String> = mapOf(
        "$COLLECTIONS.listOf" to "mutableListOf",
        "$COLLECTIONS.setOf" to "mutableSetOf",
        "$COLLECTIONS.mapOf" to "mutableMapOf"
    )
}