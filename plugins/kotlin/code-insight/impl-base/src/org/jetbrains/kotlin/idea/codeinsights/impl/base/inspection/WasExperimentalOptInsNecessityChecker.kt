// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection

import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.checkers.OptInNames


internal object WasExperimentalOptInsNecessityChecker {
    private val VERSION_ARGUMENT = Name.identifier("version")

    fun getNecessaryOptInsFromWasExperimental(
        annotations: KaAnnotationList,
        moduleApiVersion: ApiVersion,
    ): Collection<ClassId> {
        val wasExperimental = annotations.findAnnotation(StandardClassIds.Annotations.WasExperimental)
        val sinceApiVersion = getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations)

        if (wasExperimental == null || sinceApiVersion == null || moduleApiVersion >= sinceApiVersion) {
            return emptyList()
        }
        return getWasExperimentalAnnotationMarkerClassArgument(wasExperimental)
    }

    private fun getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations: KaAnnotationList): ApiVersion? {
        val sinceKotlin = annotations.findAnnotation(StandardClassIds.Annotations.SinceKotlin) ?: return null
        return sinceKotlin.argumentByName(VERSION_ARGUMENT)
            ?.asSafely<KaAnnotationValue.ConstantValue>()
            ?.value
            ?.asSafely<KaConstantValue.StringValue>()
            ?.let { ApiVersion.parse(it.value) }
    }

    private fun getWasExperimentalAnnotationMarkerClassArgument(annotation: KaAnnotation): Collection<ClassId> {
        return annotation.argumentByName(OptInNames.WAS_EXPERIMENTAL_ANNOTATION_CLASS)
            ?.asSafely<KaAnnotationValue.ArrayValue>()
            ?.values
            ?.mapNotNull { computeAnnotationMarkerClassId(it) }
            ?: emptyList()
    }

    private fun computeAnnotationMarkerClassId(value: KaAnnotationValue): ClassId? {
        val type = (value as? KaAnnotationValue.ClassLiteralValue)?.type as? KaClassType ?: return null
        return type.classId.takeIf { !it.isLocal }
    }

    private fun KaAnnotationList.findAnnotation(classId: ClassId): KaAnnotation? =
        get(classId).firstOrNull()

    private fun KaAnnotation.argumentByName(name: Name): KaAnnotationValue? =
        arguments.firstOrNull { it.name == name }?.expression
}