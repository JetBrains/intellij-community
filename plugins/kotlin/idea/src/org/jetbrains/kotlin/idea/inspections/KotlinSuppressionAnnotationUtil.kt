// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.SuppressionAnnotationUtil
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression

internal class KotlinSuppressionAnnotationUtil : SuppressionAnnotationUtil {

    private val suppressionAnnotationClassQualifiedName = Suppress::class.java.name

    override fun isSuppressionAnnotation(annotation: UAnnotation): Boolean {
        return annotation.sourcePsi?.text?.contains("Suppress") == true && // avoid resolving
                annotation.qualifiedName == suppressionAnnotationClassQualifiedName
    }

    override fun getSuppressionAnnotationAttributeExpressions(annotation: UAnnotation): List<UExpression> {
        return annotation.attributeValues
            .filter { it.name == null || it.name == "names" }
            .map { it.expression }
    }
}
