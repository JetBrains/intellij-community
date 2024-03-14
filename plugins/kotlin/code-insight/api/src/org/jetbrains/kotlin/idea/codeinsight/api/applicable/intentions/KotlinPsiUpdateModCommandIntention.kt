// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.modcommand.ModCommand
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A Kotlin intention base class for intentions that update some PSI using [ModCommand] APIs.
 */
abstract class KotlinPsiUpdateModCommandIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>
) : PsiUpdateModCommandAction<ELEMENT>(elementType.java) {
    final override fun isElementApplicable(element: ELEMENT, context: ActionContext): Boolean {
        if (!isApplicableByPsi(element)) return false

        val applicabilityRanges = getApplicabilityRange().getApplicabilityRanges(element)
        if (applicabilityRanges.isEmpty()) return false
        // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
        val relativeCaretOffset = context.offset - element.startOffset
        if (!applicabilityRanges.any { it.containsOffset(relativeCaretOffset) }) return false

        val applicableByAnalyze = analyze(element) { isApplicableByAnalyze(element) }
        return applicableByAnalyze
    }

    /**
     * The [KotlinApplicabilityRange] determines whether the tool is available in a range *after* [isApplicableByPsi] has been checked.
     *
     * Configuration of the applicability range might be as simple as choosing an existing one from `ApplicabilityRanges`.
     */
    abstract fun getApplicabilityRange(): KotlinApplicabilityRange<ELEMENT>

    /**
     * Whether this tool is applicable to [element] by PSI only. May not use the Analysis API due to performance concerns.
     */
    open fun isApplicableByPsi(element: ELEMENT): Boolean = true

    /**
     * Whether this tool is applicable to [element] by performing some resolution with the Analysis API. Any checks which don't require the
     * Analysis API should instead be implemented in [isApplicableByPsi].
     */
    context(KtAnalysisSession)
    open fun isApplicableByAnalyze(element: ELEMENT): Boolean = true
}