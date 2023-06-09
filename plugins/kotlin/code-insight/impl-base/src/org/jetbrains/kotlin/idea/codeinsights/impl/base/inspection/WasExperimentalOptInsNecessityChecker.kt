// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection

import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.checkers.OptInNames


internal object WasExperimentalOptInsNecessityChecker {
    private val VERSION_ARGUMENT = Name.identifier("version")

    fun getNecessaryOptInsFromWasExperimental(
        annotations: KtAnnotationsList,
        moduleApiVersion: ApiVersion,
    ): Collection<ClassId> {
        val wasExperimental = annotations.findAnnotation(StandardClassIds.Annotations.WasExperimental)
        val sinceApiVersion = getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations)

        if (wasExperimental == null || sinceApiVersion == null || moduleApiVersion >= sinceApiVersion) {
            return emptyList()
        }
        return getWasExperimentalAnnotationMarkerClassArgument(wasExperimental)
    }

    private fun getSinceKotlinAnnotationApiVersionArgumentIfPresent(annotations: KtAnnotationsList): ApiVersion? {
        val sinceKotlin = annotations.findAnnotation(StandardClassIds.Annotations.SinceKotlin) ?: return null
        return sinceKotlin.argumentByName(VERSION_ARGUMENT)
            ?.asSafely<KtConstantAnnotationValue>()
            ?.constantValue
            ?.asSafely<KtConstantValue.KtStringConstantValue>()
            ?.let { ApiVersion.parse(it.value) }
    }

    private fun getWasExperimentalAnnotationMarkerClassArgument(annotation: KtAnnotationApplicationWithArgumentsInfo): Collection<ClassId> {
        return annotation.argumentByName(OptInNames.WAS_EXPERIMENTAL_ANNOTATION_CLASS)
            ?.asSafely<KtArrayAnnotationValue>()
            ?.values
            ?.mapNotNull { (it as? KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue)?.classId }
            ?: emptyList()
    }

    private fun KtAnnotationsList.findAnnotation(classId: ClassId): KtAnnotationApplicationWithArgumentsInfo? =
        annotationsByClassId(classId).firstOrNull()

    private fun KtAnnotationApplicationWithArgumentsInfo.argumentByName(name: Name): KtAnnotationValue? =
        arguments.firstOrNull { it.name == name }?.expression
}