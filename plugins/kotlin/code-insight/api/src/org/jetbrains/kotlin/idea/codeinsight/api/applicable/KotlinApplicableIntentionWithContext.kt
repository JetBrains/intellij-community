// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI with [apply] given some [CONTEXT] from [prepareContext] if the intention is applicable via [isApplicableByPsi] and
 * [prepareContext].
 */
abstract class KotlinApplicableIntentionWithContext<ELEMENT : KtElement, CONTEXT>(
    elementType: KClass<ELEMENT>,
) : KotlinApplicableIntentionBase<ELEMENT>(elementType) {
    /**
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     */
    abstract fun getActionName(element: ELEMENT, context: CONTEXT): @IntentionName String

    /**
     * Provides some context for [apply]. If the intention is not applicable (by analyze), [prepareContext] should return `null`.
     * Guaranteed to be executed from a read action.
     *
     * The context should not store:
     * - Everything that came from [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] like:
     *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol], consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer] instead.
     *      - [org.jetbrains.kotlin.analysis.api.types.KtType]
     *      - [org.jetbrains.kotlin.analysis.api.calls.KtCall]
     * - The [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] instance itself.
     * - [PsiElement], consider using [com.intellij.psi.SmartPsiElementPointer] instead.
     */
    context(KtAnalysisSession)
    abstract fun prepareContext(element: ELEMENT): CONTEXT?

    /**
     * Applies a fix to [element] using information from [context]. [apply] should not use the Analysis API due to performance concerns, as
     * [apply] is usually executed on the EDT. Any information that needs to come from the Analysis API should be supplied via [prepareContext].
     *
     * [apply] is executed in a write action when [element] is physical.
     */
    abstract fun apply(element: ELEMENT, context: CONTEXT, project: Project, editor: Editor?)

    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        if (!super.isApplicableTo(element, caretOffset)) return false
        val context = prepareContextWithAnalyze(element, needsReadAction = false) ?: return false
        val actionText = getActionName(element, context)
        setTextGetter { actionText }
        return true
    }

    final override fun applyTo(element: ELEMENT, project: Project, editor: Editor?) {
        val context = prepareContextWithAnalyze(element, needsReadAction = true) ?: return
        runWriteActionIfPhysical(element) {
            apply(element, context, project, editor)
        }
    }

    final override fun startInWriteAction(): Boolean =
        // `applyTo` should start without a write action because it first uses `analyzeWithReadAction` to get the context. Also,
        // `getContext` when called from `applyTo` should not have access to a write action for `element` to discourage mutating `element`.
        false

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun prepareContextWithAnalyze(element: ELEMENT, needsReadAction: Boolean): CONTEXT? = allowAnalysisOnEdt {
        fun action() = analyze(element) { prepareContext(element) }
        if (needsReadAction) runReadAction { action() } else action()
    }
}