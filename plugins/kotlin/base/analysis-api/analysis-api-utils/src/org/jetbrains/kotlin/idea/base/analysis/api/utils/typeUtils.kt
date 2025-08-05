// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.buildStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.hasCommonSubtypeWith
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType

/**
 * Checks whether [this] is possibly a subtype of [superType] by replacing all type arguments in [superType] with star projections and
 * checking subtyping relation between [this] and obtained type. For example, `MutableList<List<T>>` is possibly a subtype of
 * `List<List<String>>`, because MutableList<List<T>>` is a subtype of `List<*>`.
 *
 * This check only approximates the possibility of the subtyping relation.
 * An accurate estimation requires the use of the constraint system, which can lead to a loss in performance.
 */
context(_: KaSession)
infix fun KaType.isPossiblySubTypeOf(superType: KaType): Boolean {
    if (this is KaTypeParameterType) return this.hasCommonSubtypeWith(superType)

    if (superType is KaTypeParameterType) return superType.symbol.upperBounds.all { this isPossiblySubTypeOf it }

    val superTypeWithReplacedTypeArguments = superType.expandedSymbol?.let { symbol ->
        buildClassTypeWithStarProjections(symbol, superType.isNullable)
    }
    return superTypeWithReplacedTypeArguments != null && isSubtypeOf(superTypeWithReplacedTypeArguments)
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
private fun buildClassTypeWithStarProjections(symbol: KaClassSymbol, nullability: Boolean): KaType =
    buildClassTypeWithStarProjections(symbol).withNullability(nullability)

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
fun buildClassTypeWithStarProjections(symbol: KaClassLikeSymbol): KaType =
    buildClassType(symbol) {
        @OptIn(KaExperimentalApi::class)
        repeat((symbol.defaultType as? KaClassType)?.qualifiers?.sumOf { it.typeArguments.size } ?: 0) {
            argument(buildStarTypeProjection())
        }
    }
