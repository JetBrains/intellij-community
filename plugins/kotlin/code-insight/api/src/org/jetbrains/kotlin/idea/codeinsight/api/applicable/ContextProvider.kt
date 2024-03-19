// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinModCommandAction.ClassBased]
 * and [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext].
 */
interface ContextProvider<E : KtElement, C> {

    /**
     * Provides some context for [apply]. If the tool is not applicable (by analyze), [prepareContext] should return `null`. Guaranteed to
     * be executed from a read action.
     *
     * The context should not store:
     * - Everything that came from [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] like:
     *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol], consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer] instead.
     *      - [org.jetbrains.kotlin.analysis.api.types.KtType]
     *      - [org.jetbrains.kotlin.analysis.api.calls.KtCall]
     * - The [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] instance itself.
     * - [com.intellij.psi.PsiElement], consider using [com.intellij.psi.SmartPsiElementPointer] instead.
     *
     * @param element a physical PSI
     */
    context(KtAnalysisSession)
    fun prepareContext(element: E): C?
}

internal fun <E : KtElement, C> ContextProvider<E, C>.getElementContext(
    element: E,
): C? = analyze(element) {
    prepareContext(element)
}