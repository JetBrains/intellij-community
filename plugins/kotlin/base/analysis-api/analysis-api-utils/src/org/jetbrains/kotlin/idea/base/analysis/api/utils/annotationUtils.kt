// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
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

private val ANNOTATION_TARGET_TYPE = CallableId(StandardClassIds.AnnotationTarget, Name.identifier(AnnotationTarget.TYPE.name))
private val ANNOTATION_TARGET_VALUE_PARAMETER =
    CallableId(StandardClassIds.AnnotationTarget, Name.identifier(AnnotationTarget.VALUE_PARAMETER.name))

@ApiStatus.Internal
context(_: KaSession)
fun KaAnnotation.isAnnotatedWithTypeUseOnly(): Boolean =
    (constructorSymbol?.containingSymbol as? KaClassSymbol)
        ?.hasApplicableAllowedTarget {
            it.isApplicableTargetSet(ANNOTATION_TARGET_TYPE) &&
                    !it.isApplicableTargetSet(ANNOTATION_TARGET_VALUE_PARAMETER)
        } ?: false