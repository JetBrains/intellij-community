// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [KotlinApplicableIntentionBase] and [KotlinApplicableInspectionBase].
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
}