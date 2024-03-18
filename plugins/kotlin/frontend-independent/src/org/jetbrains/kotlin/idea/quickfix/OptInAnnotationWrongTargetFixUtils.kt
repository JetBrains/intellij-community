// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

object OptInAnnotationWrongTargetFixUtils {
    fun collectQuickFixes(
        annotatedElement: KtDeclaration,
        annotationEntry: KtAnnotationEntry,
        annotationClassId: ClassId,
    ): List<MoveOptInRequirementToPropertyFix> {
        val annotationUseSiteTarget = annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget()
        when {
            annotatedElement is KtParameter && annotationUseSiteTarget != AnnotationUseSiteTarget.PROPERTY ->
                return listOf(
                    MoveOptInRequirementToPropertyFix(
                        MoveOptInRequirementToPropertyFix.SourceType.VALUE_PARAMETER,
                        annotationEntry,
                        annotatedElement.createSmartPointer(),
                        annotationClassId,
                        AnnotationUseSiteTarget.PROPERTY
                    )
                )

            annotatedElement is KtProperty
                    && (annotatedElement.getter?.hasBody() == true || annotationUseSiteTarget == AnnotationUseSiteTarget.PROPERTY_GETTER) ->
                return listOf(
                    MoveOptInRequirementToPropertyFix(
                        MoveOptInRequirementToPropertyFix.SourceType.GETTER,
                        annotationEntry,
                        annotatedElement.createSmartPointer(),
                        annotationClassId
                    )
                )
        }
        return emptyList()
    }
}