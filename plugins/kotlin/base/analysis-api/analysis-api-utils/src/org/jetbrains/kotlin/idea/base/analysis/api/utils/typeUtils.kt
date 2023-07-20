// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType

/**
 * Checks whether [this] is possibly a subtype of [superType] by replacing all type arguments in [superType] with star projections and
 * checking subtyping relation between [this] and obtained type. For example, `MutableList<List<T>>` is possibly a subtype of
 * `List<List<String>>`, because MutableList<List<T>>` is a subtype of `List<*>`.
 *
 * This check only approximates the possibility of the subtyping relation.
 * An accurate estimation requires the use of the constraint system, which can lead to a loss in performance.
 */
context(KtAnalysisSession)
infix fun KtType.isPossiblySubTypeOf(superType: KtType): Boolean {
    if (this is KtTypeParameterType) return this.hasCommonSubTypeWith(superType)

    if (superType is KtTypeParameterType) return superType.symbol.upperBounds.all { this isPossiblySubTypeOf it }

    val superTypeWithReplacedTypeArguments = superType.expandedClassSymbol?.let { symbol ->
        buildClassTypeWithStarProjections(symbol, superType.nullability)
    }
    return superTypeWithReplacedTypeArguments != null && this isSubTypeOf superTypeWithReplacedTypeArguments
}

context(KtAnalysisSession)
private fun buildClassTypeWithStarProjections(symbol: KtClassOrObjectSymbol, nullability: KtTypeNullability): KtType =
    buildClassType(symbol) {
        repeat(symbol.typeParameters.size) {
            argument(KtStarTypeProjection(token))
        }
    }.withNullability(nullability)