// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("AnalysisApiUtils")

package org.jetbrains.kotlin.base.analysisApiHelpers

import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplication
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.name.ClassId

fun KtAnnotated.findAnnotation(classId: ClassId): KtAnnotationApplication? {
    return annotations.find { it.classId == classId }
}

fun KtAnnotated.hasAnnotation(classId: ClassId): Boolean {
    @Suppress("SSBasedInspection")
    return findAnnotation(classId) != null
}