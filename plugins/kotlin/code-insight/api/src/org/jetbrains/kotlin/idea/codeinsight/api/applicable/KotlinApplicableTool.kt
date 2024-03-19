// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtElement

interface KotlinApplicableTool<ELEMENT : KtElement> : ApplicableRangesProvider<ELEMENT> {
    /**
     * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention],
     * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext], and
     * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection].
     */

    /**
     * Whether this tool is applicable to [element] by performing some resolution with the Analysis API. Any checks which don't require the
     * Analysis API should instead be implemented in [isApplicableByPsi].
     */
    context(KtAnalysisSession)
    fun isApplicableByAnalyze(element: ELEMENT): Boolean = true
}

internal fun <ELEMENT : KtElement> KotlinApplicableTool<ELEMENT>.isApplicableWithAnalyze(element: ELEMENT): Boolean =
    analyze(element) {
        isApplicableByAnalyze(element)
    }
