// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A [AbstractKotlinApplicableInspection] that applies to an element if it has a specific [DIAGNOSTIC].
 */
abstract class AbstractKotlinApplicableDiagnosticInspection<ELEMENT : KtElement, DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableInspection<ELEMENT>(elementType), AbstractKotlinApplicableDiagnosticInspectionBase<ELEMENT, DIAGNOSTIC> {
    /**
     * Whether this inspection is applicable to [element] given a [diagnostic].
     *
     * @see org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool.isApplicableByAnalyze
     */
    context(KtAnalysisSession)
    abstract fun isApplicableByDiagnostic(element: ELEMENT, diagnostic: DIAGNOSTIC): Boolean

    context(KtAnalysisSession)
    final override fun isApplicableByAnalyze(element: ELEMENT): Boolean {
        val diagnostic = this.getDiagnostic(element) ?: return false
        return isApplicableByDiagnostic(element, diagnostic)
    }
}