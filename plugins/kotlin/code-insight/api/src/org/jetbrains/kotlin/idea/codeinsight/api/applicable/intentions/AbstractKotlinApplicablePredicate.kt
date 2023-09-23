// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractKotlinApplicablePredicate<ELEMENT : KtElement> {
    fun apply(element: ELEMENT, context: ActionContext): Boolean {
        if (!isApplicableByPsi(element)) return false

        val applicabilityRanges = getApplicabilityRange().getApplicabilityRanges(element)
        if (applicabilityRanges.isEmpty()) return false
        // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
        val relativeCaretOffset = context.offset - element.startOffset
        if (applicabilityRanges.none { it.containsOffset(relativeCaretOffset) }) return false

        val applicableByAnalyze = analyze(element) { isApplicableByAnalyze(element) }
        return applicableByAnalyze
    }

    abstract fun getApplicabilityRange(): KotlinApplicabilityRange<ELEMENT>

    open fun isApplicableByPsi(element: ELEMENT): Boolean = true

    /**
     * Whether this tool is applicable to [element] by performing some resolution with the Analysis API. Any checks which don't require the
     * Analysis API should instead be implemented in [isApplicableByPsi].
     */
    context(KtAnalysisSession)
    open fun isApplicableByAnalyze(element: ELEMENT): Boolean = true
}