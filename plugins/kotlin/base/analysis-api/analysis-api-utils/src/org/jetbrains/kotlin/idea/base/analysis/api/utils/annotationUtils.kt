// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds.Annotations.ParameterNames

@ApiStatus.Internal
fun KaAnnotationValue.isApplicableTargetSet(expectedTargetCallableId: CallableId): Boolean {
    return when (this) {
        is KaAnnotationValue.ArrayValue -> values.any { it.isApplicableTargetSet(expectedTargetCallableId) }
        is KaAnnotationValue.EnumEntryValue -> callableId == expectedTargetCallableId
        else -> false
    }
}

fun KaAnnotated.hasApplicableAllowedTarget(annotationValueFilter: (KaAnnotationValue) -> Boolean): Boolean =
    annotations
        .firstOrNull { it.classId == StandardClassIds.Annotations.Target }
        ?.arguments
        ?.filter { it.name == ParameterNames.targetAllowedTargets }
        ?.any { annotationValueFilter(it.expression) } ?: false
