// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionBase] and
 * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionBase].
 */
interface KotlinApplicableToolBase<ELEMENT : KtElement> {
    /**
     * The [KotlinApplicabilityRange] determines whether the tool is available in a range *after* [isApplicableByPsi] has been checked.
     *
     * Configuration of the applicability range might be as simple as choosing an existing one from `ApplicabilityRanges`.
     */
    fun getApplicabilityRange(): KotlinApplicabilityRange<ELEMENT>

    /**
     * Whether this tool is applicable to [element] by PSI only. May not use the Analysis API due to performance concerns.
     */
    fun isApplicableByPsi(element: ELEMENT): Boolean

    /**
     * Whether `apply` should be performed in a write action. An individual tool may override this to have fine-grained control over read
     * and write actions in `apply`. For example, a tool may wish to start a refactoring in `apply` outside a write action.
     */
    fun shouldApplyInWriteAction(): Boolean = true
}

@ApiStatus.Internal
fun <ELEMENT : KtElement> KotlinApplicableToolBase<ELEMENT>.isApplicableToElement(element: ELEMENT, caretOffset: Int): Boolean {
    if (!isApplicableByPsi(element)) return false
    val ranges = getApplicabilityRange().getApplicabilityRanges(element)
    if (ranges.isEmpty()) return false

    // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
    val relativeCaretOffset = caretOffset - element.startOffset
    return ranges.any { it.containsOffset(relativeCaretOffset) }
}
