// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.allSupertypes
import org.jetbrains.kotlin.analysis.api.types.directSupertypes
import org.jetbrains.kotlin.analysis.api.types.isArrayOrPrimitiveArray
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

private val ITERABLE_CLASS_IDS: Set<ClassId> = buildSet {
    this += StandardClassIds.Iterable
    this += StandardClassIds.Map
    this += StandardClassIds.Sequence
    this += ClassId.fromString("java/util/stream/Stream")
    this += KaStandardTypeClassIds.CHAR_SEQUENCE
}
private val ITERABLE_AND_ITERATOR_CLASS_IDS: Set<ClassId> = ITERABLE_CLASS_IDS + StandardClassIds.Iterator
private val ITERABLE_AND_ARRAYS_CLASS_IDS: Set<ClassId> = buildSet {
    this += StandardClassIds.Array
    this += StandardClassIds.primitiveArrayTypeByElementType.values // What about elementTypeByUnsignedArrayType?
    this += ITERABLE_CLASS_IDS
}

@ApiStatus.Internal
context(_: KaSession)
fun canBeIterated(type: KaType, checkNullability: Boolean = true): Boolean {
    return type.isArrayType(checkNullability) || type.isInheritorOf(
        ITERABLE_CLASS_IDS,
        hashMapOf(StandardClassIds.Any to false),
        checkNullability
    )
}

context(_: KaSession)
private fun KaType.isArrayType(checkNullability: Boolean): Boolean =
    (!checkNullability || !isMarkedNullable) && isArrayOrPrimitiveArray

@ApiStatus.Internal
context(_: KaSession)
fun iterationElementType(classType: KaClassType): KaType? =
    typeArgumentFrom(classType, ITERABLE_AND_ARRAYS_CLASS_IDS, index = 0)

context(_: KaSession)
private fun typeArgumentFrom(classType: KaClassType, classIds: Set<ClassId>, index: Int): KaType? =
    selfAndSupertypes(classType).firstNotNullOfOrNull { type ->
        if (type.classId in classIds) type.typeArguments.getOrNull(index)?.type else null
    }

context(_: KaSession)
private fun selfAndSupertypes(classType: KaClassType): Sequence<KaClassType> =
    sequenceOf(classType) + classType.allSupertypes(shouldApproximate = true).filterIsInstance<KaClassType>()

@ApiStatus.Internal
context(_: KaSession)
fun canBeIteratedOrIterator(type: KaType, checkNullability: Boolean = true): Boolean {
    return type.isArrayType(checkNullability) || type.isInheritorOf(
        ITERABLE_AND_ITERATOR_CLASS_IDS,
        hashMapOf(StandardClassIds.Any to false),
        checkNullability
    )
}

@ApiStatus.Internal
context(_: KaSession)
private fun KaType.isInheritorOf(
    classIds: Set<ClassId>,
    visitedTypes: MutableMap<ClassId, Boolean>,
    checkNullability: Boolean = true
): Boolean {
    ProgressManager.checkCanceled()
    return when (this) {
        is KaFlexibleType -> this.lowerBoundIfFlexible().isInheritorOf(classIds, visitedTypes)
        is KaIntersectionType -> this.conjuncts.any { it.isInheritorOf(classIds, visitedTypes) }
        is KaDefinitelyNotNullType -> this.original.isInheritorOf(
            classIds,
            if (checkNullability) hashMapOf() else visitedTypes,
            checkNullability = false
        )
        is KaTypeParameterType -> symbol.upperBounds.any { it.isInheritorOf(classIds, visitedTypes) }
        is KaClassType -> {
            val classId = classId
            visitedTypes[classId]?.let { return it }

            val inheritor = (!checkNullability || !isMarkedNullable)
                    && (classId in classIds || directSupertypes(shouldApproximate = true).any {
                        it.isInheritorOf(classIds, visitedTypes)
                    })

            visitedTypes[classId] = inheritor
            inheritor
        }
        else -> false
    }
}