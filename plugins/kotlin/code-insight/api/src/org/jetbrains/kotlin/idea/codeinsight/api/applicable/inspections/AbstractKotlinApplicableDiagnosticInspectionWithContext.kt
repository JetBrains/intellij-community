// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement

/**
 * A [AbstractKotlinApplicableInspectionWithContext] that applies to an element if it has a specific [DIAGNOSTIC].
 */
abstract class AbstractKotlinApplicableDiagnosticInspectionWithContext<ELEMENT : KtElement, DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>, CONTEXT>
    : AbstractKotlinApplicableInspectionWithContext<ELEMENT, CONTEXT>(), AbstractKotlinApplicableDiagnosticInspection<ELEMENT, DIAGNOSTIC> {
    /**
     * Provides some context for [apply] given some [element] and [diagnostic].
     *
     * @see org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolWithContext.prepareContext
     */
    context(KtAnalysisSession)
    abstract fun prepareContextByDiagnostic(element: ELEMENT, diagnostic: DIAGNOSTIC): CONTEXT?

    context(KtAnalysisSession)
    final override fun prepareContext(element: ELEMENT): CONTEXT? {
        val diagnostics = element.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
        val suitableDiagnostics = diagnostics.filterIsInstance(this.getDiagnosticType().java)
        val diagnostic = suitableDiagnostics.firstOrNull() ?: return null
        return prepareContextByDiagnostic(element, diagnostic)
    }
}