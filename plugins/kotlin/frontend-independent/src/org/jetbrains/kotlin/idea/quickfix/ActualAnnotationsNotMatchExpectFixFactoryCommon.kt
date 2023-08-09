// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.multiplatform.ExpectActualAnnotationsIncompatibilityType

object ActualAnnotationsNotMatchExpectFixFactoryCommon {
    fun createRemoveAnnotationFromExpectFix(expectAnnotationEntry: KtAnnotationEntry): QuickFixActionBase<*>? {
        val annotationName = expectAnnotationEntry.shortName ?: return null
        return RemoveAnnotationFix(
            KotlinBundle.message("fix.remove.mismatched.annotation.from.expect.declaration.may.change.semantics", annotationName),
            expectAnnotationEntry
        )
    }

    fun createCopyAndReplaceAnnotationFixes(
        actualDeclaration: KtNamedDeclaration,
        expectAnnotationEntry: KtAnnotationEntry,
        incompatibilityType: ExpectActualAnnotationsIncompatibilityType<KtAnnotationEntry?>,
        annotationClassIdProvider: () -> ClassId?,
    ): List<KotlinQuickFixAction<*>> {
        val actualAnnotationEntry = when (incompatibilityType) {
            is ExpectActualAnnotationsIncompatibilityType.MissingOnActual -> null
            is ExpectActualAnnotationsIncompatibilityType.DifferentOnActual -> incompatibilityType.actualAnnotation
        }

        val fixOnActualFix = if (actualAnnotationEntry == null) {
            createCopyFromExpectToActualFix(expectAnnotationEntry, actualDeclaration, annotationClassIdProvider)
        } else {
            val annotationName = expectAnnotationEntry.shortName ?: return emptyList()
            ReplaceAnnotationArgumentsInExpectActualFix(
                KotlinBundle.message("fix.replace.mismatched.annotation.args.on.actual.declaration.may.change.semantics", annotationName),
                copyFromAnnotationEntry = expectAnnotationEntry,
                copyToAnnotationEntry = actualAnnotationEntry,
            )
        }

        return listOfNotNull(fixOnActualFix)
    }

    private fun createCopyFromExpectToActualFix(
        expectAnnotationEntry: KtAnnotationEntry, actualDeclaration: KtNamedDeclaration, annotationClassIdProvider: () -> ClassId?
    ): KotlinQuickFixAction<*>? {
        val annotationClassId = annotationClassIdProvider.invoke() ?: return null

        return CopyAnnotationFromExpectToActualFix(
            actualDeclaration,
            expectAnnotationEntry,
            annotationClassId,
        )
    }
}
