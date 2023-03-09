// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtElement

/**
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention] and
 * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection].
 */
interface KotlinApplicableTool<ELEMENT : KtElement> : KotlinApplicableToolBase<ELEMENT> {
    /**
     * The text to be shown in the list of available fixes.
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     * @see com.intellij.codeInspection.QuickFix.getName
     */
    fun getActionName(element: ELEMENT): @IntentionName String

    /**
     * Whether this tool is applicable to [element] by performing some resolution with the Analysis API. Any checks which don't require the
     * Analysis API should instead be implemented in [isApplicableByPsi].
     */
    context(KtAnalysisSession)
    fun isApplicableByAnalyze(element: ELEMENT): Boolean = true

    /**
     * Applies a fix to [element]. [apply] should not use the Analysis API due to performance concerns, as [apply] is usually executed on
     * the EDT. [apply] is guaranteed to be executed in a write action if [element] is physical.
     */
    fun apply(element: ELEMENT, project: Project, editor: Editor?)
}

internal fun <ELEMENT : KtElement> KotlinApplicableTool<ELEMENT>.isApplicableWithAnalyze(element: ELEMENT): Boolean =
    analyze(element) {
        isApplicableByAnalyze(element)
    }

@OptIn(KtAllowAnalysisOnEdt::class)
internal fun <ELEMENT : KtElement> KotlinApplicableTool<ELEMENT>.isApplicableWithAnalyzeAllowEdt(element: ELEMENT): Boolean =
    allowAnalysisOnEdt {
        isApplicableWithAnalyze(element)
    }
