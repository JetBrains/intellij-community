// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.copyOrigin
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.chooseDanglingFileResolutionMode
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiBasedModCommandAction.ClassBased]
 * and [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase].
 */
interface ContextProvider<E : KtElement, C : Any> {

    /**
     * Provides some context for [apply]. If the tool is not applicable (by analyze), [prepareContext] should return `null`. Guaranteed to
     * be executed from a read action.
     *
     * The context should not store:
     * - Everything that came from [org.jetbrains.kotlin.analysis.api.KaSession] like:
     *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol], consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer] instead.
     *      - [org.jetbrains.kotlin.analysis.api.types.KaType]
     *      - [org.jetbrains.kotlin.analysis.api.resolution.KaCall]
     * - The [org.jetbrains.kotlin.analysis.api.KaSession] instance itself.
     * - [com.intellij.psi.PsiElement], consider using [com.intellij.psi.SmartPsiElementPointer] instead.
     *
     * @param element a physical PSI
     */
    fun KaSession.prepareContext(element: E): C?
}

@OptIn(KaExperimentalApi::class)
fun <E : KtElement, C : Any> ContextProvider<E, C>.getElementContext(
    element: E,
): C? = if (element.containingFile.copyOrigin == null) analyze(element) {
    prepareContext(element)
} else analyzeCopy(element, chooseDanglingFileResolutionMode(element)) {
    prepareContext(element)
}

inline val Boolean.asUnit: Unit?
    get() = if (this) Unit else null
