// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
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
    this += ClassId.fromString("kotlin/sequences/Sequence")
    this += ClassId.fromString("java/util/stream/Stream")
    this += DefaultTypeClassIds.CHAR_SEQUENCE
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
@ApiStatus.Internal
fun canBeIterated(type: KaType, checkNullability: Boolean = true): Boolean {
    return when (type) {
        is KaFlexibleType -> canBeIterated(type.lowerBoundIfFlexible())
        is KaIntersectionType -> type.conjuncts.all { canBeIterated(it) }
        is KaDefinitelyNotNullType -> canBeIterated(type.original, checkNullability = false)
        is KaTypeParameterType -> type.symbol.upperBounds.any { canBeIterated(it) }
        is KaClassType -> {
            (!checkNullability || !type.isMarkedNullable)
                    && (type.classId in ITERABLE_CLASS_IDS || type.allSupertypes(shouldApproximate = true).any { canBeIterated(it) })
        }
        else -> false
    }
}