// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * A [KotlinApplicableInspectionBase.Simple] that applies to an element if it has a specific diagnostic [D].
 * Use this class when [D] is a `KaFirDiagnostic<PsiElement>` and not a `KaFirDiagnostic<Kt...>`.
 *
 * For `KaFirDiagnostic<Kt...>`, use [KotlinKtDiagnosticBasedInspectionBase].
 *
 * It is important to carefully choose a parameter of type [E]
 * so that it corresponds to the type of element that has the diagnostic [D].
 */
abstract class KotlinPsiDiagnosticBasedInspectionBase<
        E : KtElement,
        D : KaDiagnosticWithPsi<PsiElement>,
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
    context(KaSession)
    abstract fun prepareContextByDiagnostic(
        element: E,
        diagnostic: D,
    ): C?

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    final override fun prepareContext(element: E): C? =
        element.diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .firstNotNullOfOrNull { diagnosticType.safeCast(it) }
            ?.let { prepareContextByDiagnostic(element, it) }
}