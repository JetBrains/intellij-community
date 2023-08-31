// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.quickfix.ActualAnnotationsNotMatchExpectFixFactoryCommon
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionsFactory
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.multiplatform.ExpectActualAnnotationsIncompatibilityType
import org.jetbrains.kotlin.resolve.source.getPsi

internal object ActualAnnotationsNotMatchExpectFixFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val castedDiagnostic = DiagnosticFactory.cast(diagnostic, Errors.ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT)
        val incompatibilityType = castedDiagnostic.c

        val expectAnnotationEntry = incompatibilityType.expectAnnotation.source.getPsi() as? KtAnnotationEntry
            ?: return emptyList()

        val removeAnnotationFix =
            ActualAnnotationsNotMatchExpectFixFactoryCommon.createRemoveAnnotationFromExpectFix(expectAnnotationEntry)

        return listOfNotNull(removeAnnotationFix) +
                createCopyAndReplaceAnnotationFixes(expectAnnotationEntry, castedDiagnostic.a, castedDiagnostic.b, incompatibilityType)
    }

    private fun createCopyAndReplaceAnnotationFixes(
        expectAnnotationEntry: KtAnnotationEntry,
        expectDeclarationDescriptor: DeclarationDescriptor,
        actualDeclarationDescriptor: DeclarationDescriptor,
        incompatibilityType: ExpectActualAnnotationsIncompatibilityType<AnnotationDescriptor>,
    ): List<QuickFixActionBase<*>> {
        val expectDeclaration = expectDeclarationDescriptor.toSourceElement.getPsi() as? KtNamedDeclaration ?: return emptyList()
        val actualDeclaration = actualDeclarationDescriptor.toSourceElement.getPsi() as? KtNamedDeclaration ?: return emptyList()
        val mappedIncompatibilityType = incompatibilityType.mapAnnotationType {
            it.source.getPsi() as? KtAnnotationEntry
        }

        return ActualAnnotationsNotMatchExpectFixFactoryCommon.createCopyAndReplaceAnnotationFixes(
            expectDeclaration,
            actualDeclaration,
            expectAnnotationEntry,
            mappedIncompatibilityType,
            annotationClassIdProvider = { getAnnotationClassId(expectAnnotationEntry) }
        )
    }

    private fun getAnnotationClassId(annotationEntry: KtAnnotationEntry): ClassId? {
        analyze(annotationEntry) {
            val resolvedExpectAnnotationCall = annotationEntry.resolveCall()?.singleConstructorCallOrNull() ?: return null
            return resolvedExpectAnnotationCall.symbol.containingClassIdIfNonLocal
        }
    }
}