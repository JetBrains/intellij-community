// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.base.psi.callableIdIfNotLocal
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
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
        expectDeclaration: KtNamedDeclaration,
        actualDeclaration: KtNamedDeclaration,
        expectAnnotationEntry: KtAnnotationEntry,
        incompatibilityType: ExpectActualAnnotationsIncompatibilityType<KtAnnotationEntry?>,
        annotationClassIdProvider: () -> ClassId?,
    ): List<KotlinQuickFixAction<*>> {
        if (skipFakeOverrideAndTypealias(expectDeclaration, actualDeclaration)) {
            return emptyList()
        }

        val actualAnnotationEntry = when (incompatibilityType) {
            is ExpectActualAnnotationsIncompatibilityType.MissingOnActual -> null
            is ExpectActualAnnotationsIncompatibilityType.DifferentOnActual -> incompatibilityType.actualAnnotation
        }

        if (actualAnnotationEntry == null) {
            val copyFromExpect = createCopyFromExpectToActualFix(expectAnnotationEntry, actualDeclaration, annotationClassIdProvider)
            return listOfNotNull(copyFromExpect)
        }

        val annotationName = expectAnnotationEntry.shortName ?: return emptyList()
        val fixOnActual = ReplaceAnnotationArgumentsInExpectActualFix(
            KotlinBundle.message("fix.replace.mismatched.annotation.args.on.actual.declaration.may.change.semantics", annotationName),
            copyFromAnnotationEntry = expectAnnotationEntry,
            copyToAnnotationEntry = actualAnnotationEntry,
        )
        val fixOnExpect = ReplaceAnnotationArgumentsInExpectActualFix(
            KotlinBundle.message("fix.replace.mismatched.annotation.args.on.expect.declaration.may.change.semantics", annotationName),
            copyFromAnnotationEntry = actualAnnotationEntry,
            copyToAnnotationEntry = expectAnnotationEntry,
        )
        return listOf(fixOnActual, fixOnExpect)
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

    /**
     *  When actual is typealias, quick fix changes code somewhere in another declaration (nor expect, nor actual), which might be
     *  unrelated to the KMP world.
     *  Such a modification is incomprehensible and undesirable for the user.
     *  Same with actual fake overrides: user wants to fix the incompatibility between expected and actual classes,
     *  but quick fix changes some third class that probably serves some other purpose than just being a common parent.
     */
    private fun skipFakeOverrideAndTypealias(expectDeclaration: KtNamedDeclaration, actualDeclaration: KtNamedDeclaration): Boolean {
        fun notEqual(a: Any?, b: Any?): Boolean {
            check(a != null && b != null) { "expect and actual cannot be local, so must always have non-null ClassId"}
            return a != b
        }

        return when {
            expectDeclaration is KtClassOrObject && actualDeclaration is KtClassOrObject -> {
                notEqual(expectDeclaration.classIdIfNonLocal, actualDeclaration.classIdIfNonLocal)
            }

            expectDeclaration is KtCallableDeclaration && actualDeclaration is KtCallableDeclaration -> {
                notEqual(expectDeclaration.callableIdIfNotLocal, actualDeclaration.callableIdIfNotLocal)
            }

            else -> error("Unexpected types: $expectDeclaration $actualDeclaration")
        }
    }
}
