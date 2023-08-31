// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.quickfix.ActualAnnotationsNotMatchExpectFixFactoryCommon
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal object ActualAnnotationsNotMatchExpectFixFactory {
    val factory = diagnosticFixFactory(KtFirDiagnostic.ActualAnnotationsNotMatchExpect::class, ::createQuickFixes)

    context (KtAnalysisSession)
    private fun createQuickFixes(diagnostic: KtFirDiagnostic.ActualAnnotationsNotMatchExpect): List<QuickFixActionBase<*>> {
        val expectAnnotationEntry = diagnostic.incompatibilityType.expectAnnotation.psi as? KtAnnotationEntry
            ?: return emptyList()

        val removeAnnotationFix =
            ActualAnnotationsNotMatchExpectFixFactoryCommon.createRemoveAnnotationFromExpectFix(expectAnnotationEntry)

        return listOfNotNull(removeAnnotationFix) + createCopyAndReplaceAnnotationFixes(diagnostic, expectAnnotationEntry)
    }

    context (KtAnalysisSession)
    private fun createCopyAndReplaceAnnotationFixes(
        diagnostic: KtFirDiagnostic.ActualAnnotationsNotMatchExpect,
        expectAnnotationEntry: KtAnnotationEntry,
    ): List<QuickFixActionBase<*>> {
        val expectDeclaration = diagnostic.expectSymbol.psi as? KtNamedDeclaration ?: return emptyList()
        val actualDeclaration = diagnostic.actualSymbol.psi as? KtNamedDeclaration ?: return emptyList()
        val mappedIncompatibilityType = diagnostic.incompatibilityType.mapAnnotationType {
            it.psi as? KtAnnotationEntry
        }
        return ActualAnnotationsNotMatchExpectFixFactoryCommon.createCopyAndReplaceAnnotationFixes(
            expectDeclaration,
            actualDeclaration,
            expectAnnotationEntry,
            mappedIncompatibilityType,
            annotationClassIdProvider = { expectAnnotationEntry.getAnnotationClassId() }
        )
    }

    context (KtAnalysisSession)
    private fun KtAnnotationEntry.getAnnotationClassId(): ClassId? {
        val resolvedExpectAnnotationCall = resolveCall()?.singleConstructorCallOrNull() ?: return null
        return resolvedExpectAnnotationCall.symbol.containingClassIdIfNonLocal
    }
}