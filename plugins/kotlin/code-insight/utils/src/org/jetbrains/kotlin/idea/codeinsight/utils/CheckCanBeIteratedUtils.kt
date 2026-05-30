// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

private val ITERABLE_CLASS_IDS: Set<ClassId> = buildSet {
    this += StandardClassIds.Array
    this += StandardClassIds.primitiveArrayTypeByElementType.values // What about elementTypeByUnsignedArrayType?
    this += StandardClassIds.Iterable
    this += StandardClassIds.Map
    this += StandardClassIds.Sequence
    this += ClassId.fromString("java/util/stream/Stream")
    this += KaStandardTypeClassIds.CHAR_SEQUENCE
}

@ApiStatus.Internal
context(_: KaSession)
fun canBeIterated(type: KaType, checkNullability: Boolean = true): Boolean =
    type.isInheritorOf(ITERABLE_CLASS_IDS, checkNullability)

@ApiStatus.Internal
context(_: KaSession)
fun iterationElementType(classType: KaClassType): KaType? =
    typeArgumentFrom(classType, ITERABLE_CLASS_IDS, index = 0)

context(_: KaSession)
private fun typeArgumentFrom(classType: KaClassType, classIds: Set<ClassId>, index: Int): KaType? =
    selfAndSupertypes(classType).firstNotNullOfOrNull { type ->
        if (type.classId in classIds) type.typeArguments.getOrNull(index)?.type else null
    }

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
private fun selfAndSupertypes(classType: KaClassType): Sequence<KaClassType> =
    sequenceOf(classType) + classType.allSupertypes(shouldApproximate = true).filterIsInstance<KaClassType>()

@ApiStatus.Internal
context(_: KaSession)
fun canBeIteratedOrIterator(type: KaType, checkNullability: Boolean = true): Boolean =
    type.isInheritorOf(ITERABLE_CLASS_IDS + StandardClassIds.Iterator, checkNullability)

@OptIn(KaContextParameterApi::class)
@ApiStatus.Internal
context(_: KaSession)
private fun KaType.isInheritorOf(classIds: Set<ClassId>, checkNullability: Boolean = true): Boolean {
    return when (this) {
        is KaFlexibleType -> this.lowerBoundIfFlexible().isInheritorOf(classIds)
        is KaIntersectionType -> this.conjuncts.any { it.isInheritorOf(classIds) }
        is KaDefinitelyNotNullType -> this.original.isInheritorOf(classIds, checkNullability = false)
        is KaTypeParameterType -> symbol.upperBounds.any { it.isInheritorOf(classIds) }
        is KaClassType -> {
            (!checkNullability || !isMarkedNullable)
                    && (classId in classIds || allSupertypes(shouldApproximate = true).any { it.isInheritorOf(classIds) })
        }
        else -> false
    }
}