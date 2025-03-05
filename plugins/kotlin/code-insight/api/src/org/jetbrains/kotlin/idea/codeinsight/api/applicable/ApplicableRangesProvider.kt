// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction] and
 * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase].
 */
interface ApplicableRangesProvider<E : KtElement> {

    /**
     * Determines whether the tool is available in a range *after* [isApplicableByPsi] has been checked.
     *
     * Configuration of the applicability range might be as simple as choosing an existing one from `ApplicabilityRanges`.
     *
     * Important! Must return text ranges relative to passed [element],
     * i.e., if a range covers the whole element then it should return `[0, element.length)`.
     */
    fun getApplicableRanges(element: E): List<TextRange> = ApplicabilityRange.self(element)

    /**
     * Whether this tool is applicable to [element] by PSI only. May not use the Analysis API due to performance concerns.
     */
    fun isApplicableByPsi(element: E): Boolean = true
}
