// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * [KotlinApplicableDiagnosticInspectionBase] is a base interface for [AbstractKotlinApplicableDiagnosticInspection] and
 * [AbstractKotlinApplicableDiagnosticInspectionWithContext].
 *
 * TODO: Consider supporting multiple diagnostics.
 */
interface KotlinApplicableDiagnosticInspectionBase<
    ELEMENT : KtElement,
    DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>
> : KotlinApplicableToolBase<ELEMENT> {
    /**
     * The type of the [KtDiagnosticWithPsi] which should be filtered for.
     */
    fun getDiagnosticType(): KClass<DIAGNOSTIC>

    override fun isApplicableByPsi(element: ELEMENT): Boolean = true
}

context(KtAnalysisSession)
internal fun <
    ELEMENT : KtElement,
    DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>
> KotlinApplicableDiagnosticInspectionBase<ELEMENT, DIAGNOSTIC>.getDiagnostic(element: ELEMENT): DIAGNOSTIC? {
    val diagnostics = element.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
    val suitableDiagnostics = diagnostics.filterIsInstance(getDiagnosticType().java)
    return suitableDiagnostics.firstOrNull()
}