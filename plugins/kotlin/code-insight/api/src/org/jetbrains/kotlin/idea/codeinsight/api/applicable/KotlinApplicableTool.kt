// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention] and
 * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection].
 */
interface KotlinApplicableTool<ELEMENT : KtElement> : KotlinApplicableToolBase<ELEMENT> {

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

@OptIn(KtAllowAnalysisOnEdt::class)
internal fun <ELEMENT : KtElement> KotlinApplicableTool<ELEMENT>.isApplicableWithAnalyzeAllowEdt(element: ELEMENT): Boolean =
    allowAnalysisOnEdt {
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            isApplicableWithAnalyze(element)
        }
    }
