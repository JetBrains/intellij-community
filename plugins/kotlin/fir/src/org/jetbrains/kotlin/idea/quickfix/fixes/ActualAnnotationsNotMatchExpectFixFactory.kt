// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.quickfix.ActualAnnotationsNotMatchExpectFixFactoryCommon
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal object ActualAnnotationsNotMatchExpectFixFactory {
    val factory = diagnosticFixFactory(KtFirDiagnostic.ActualAnnotationsNotMatchExpect::class, ::createQuickFixes)

    context (KtAnalysisSession)
    private fun createQuickFixes(diagnostic: KtFirDiagnostic.ActualAnnotationsNotMatchExpect): List<QuickFixActionBase<*>> {
        val expectAnnotationEntry = diagnostic.incompatibilityType.expectAnnotation.psi as? KtAnnotationEntry
            ?: return emptyList()

        val removeAnnotationFix =
            ActualAnnotationsNotMatchExpectFixFactoryCommon.createRemoveAnnotationFromExpectFix(expectAnnotationEntry)

        return listOfNotNull(removeAnnotationFix)
    }
}