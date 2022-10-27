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
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI with [apply] if the intention is applicable via [isApplicableByPsi] and [isApplicableByAnalyze].
 *
 * If [apply] needs to use the Analysis API, inherit from [KotlinApplicableIntentionWithContext] instead.
 */
abstract class KotlinApplicableIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : KotlinApplicableIntentionBase<ELEMENT>(elementType) {
    /**
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     */
    abstract fun getActionName(element: ELEMENT): @IntentionName String

    /**
     * Whether this intention is applicable to [element] by performing some resolution with the Analysis API. Any checks which don't
     * require the Analysis API should instead be implemented in [isApplicableByPsi].
     */
    context(KtAnalysisSession)
    open fun isApplicableByAnalyze(element: ELEMENT): Boolean = true

    /**
     * Applies a fix to [element]. [apply] should not use the Analysis API due to performance concerns, as [apply] is usually executed on
     * the EDT.
     *
     * [apply] is always executed in a write action, except when [startInWriteAction] is overridden.
     */
    abstract fun apply(element: ELEMENT, project: Project, editor: Editor?)

    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        if (!super.isApplicableTo(element, caretOffset)) return false
        if (!isApplicableWithAnalyze(element)) return false

        val actionText = getActionName(element)
        setTextGetter { actionText }
        return true
    }

    final override fun applyTo(element: ELEMENT, project: Project, editor: Editor?) = apply(element, project, editor)

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun isApplicableWithAnalyze(element: ELEMENT): Boolean = allowAnalysisOnEdt {
        analyze(element) {
            isApplicableByAnalyze(element)
        }
    }
}