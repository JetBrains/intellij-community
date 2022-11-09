// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A [AbstractKotlinApplicableInspectionWithContext] that applies to an element if it has a specific [DIAGNOSTIC].
 */
abstract class AbstractKotlinApplicableDiagnosticInspectionWithContext<ELEMENT : KtElement, DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>, CONTEXT>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableInspectionWithContext<ELEMENT, CONTEXT>(elementType), KotlinApplicableDiagnosticInspectionBase<ELEMENT, DIAGNOSTIC> {
    /**
     * Provides some context for [apply] given some [element] and [diagnostic].
     *
     * @see KotlinApplicableToolWithContext.prepareContext
     */
    context(KtAnalysisSession)
    abstract fun prepareContextByDiagnostic(element: ELEMENT, diagnostic: DIAGNOSTIC): CONTEXT?

    context(KtAnalysisSession)
    final override fun prepareContext(element: ELEMENT): CONTEXT? {
        val diagnostic = this.getDiagnostic(element) ?: return null
        return prepareContextByDiagnostic(element, diagnostic)
    }
}