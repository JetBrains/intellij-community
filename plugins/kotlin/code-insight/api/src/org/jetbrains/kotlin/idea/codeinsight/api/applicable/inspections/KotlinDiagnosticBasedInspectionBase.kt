// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * A [KotlinApplicableInspectionBase.Simple] that applies to an element if it has a specific [D].
 */
abstract class KotlinDiagnosticBasedInspectionBase<
        E : KtElement,
        D : KtDiagnosticWithPsi<E>,
        C : Any,
        > : KotlinApplicableInspectionBase.Simple<E, C>() {

    protected abstract val diagnosticType: KClass<D>

    /**
     * Provides some context for [apply] given some [element] and [diagnostic].
     *
     * @see org.jetbrains.kotlin.idea.codeinsight.api.applicable.ContextProvider.prepareContext
     *
     * @param element a physical PSI
     */
    context(KtAnalysisSession)
    abstract fun prepareContextByDiagnostic(
        element: E,
        diagnostic: D,
    ): C?

    context(KtAnalysisSession)
    final override fun prepareContext(element: E): C? =
        element.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .firstNotNullOfOrNull { diagnosticType.safeCast(it) }
            ?.let { prepareContextByDiagnostic(element, it) }
}