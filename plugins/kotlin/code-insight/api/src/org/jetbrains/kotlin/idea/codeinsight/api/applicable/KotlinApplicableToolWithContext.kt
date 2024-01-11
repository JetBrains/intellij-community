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
 * A common base interface for [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext]
 * and [org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext].
 */
interface KotlinApplicableToolWithContext<ELEMENT : KtElement, CONTEXT> : KotlinApplicableToolBase<ELEMENT> {
    /**
     * The text to be shown in the list of available fixes.
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     * @see com.intellij.codeInspection.QuickFix.getName
     */
    fun getActionName(element: ELEMENT, context: CONTEXT): @IntentionName String

    /**
     * Provides some context for [apply]. If the tool is not applicable (by analyze), [prepareContext] should return `null`. Guaranteed to
     * be executed from a read action.
     *
     * The context should not store:
     * - Everything that came from [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] like:
     *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol], consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer] instead.
     *      - [org.jetbrains.kotlin.analysis.api.types.KtType]
     *      - [org.jetbrains.kotlin.analysis.api.calls.KtCall]
     * - The [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] instance itself.
     * - [PsiElement], consider using [com.intellij.psi.SmartPsiElementPointer] instead.
     *
     * @param element a physical PSI
     */
    context(KtAnalysisSession)
    fun prepareContext(element: ELEMENT): CONTEXT?

    /**
     * Applies a fix to [element] using information from [context]. [apply] should not use the Analysis API due to performance concerns, as
     * [apply] is usually executed on the EDT. Any information that needs to come from the Analysis API should be supplied via
     * [prepareContext]. [apply] is executed in a write action if [element] is physical and [shouldApplyInWriteAction] returns `true`.
     */
    fun apply(element: ELEMENT, context: CONTEXT, project: Project, editor: Editor?)
}

internal fun <ELEMENT : KtElement, CONTEXT> KotlinApplicableToolWithContext<ELEMENT, CONTEXT>.prepareContextWithAnalyze(
    element: ELEMENT,
): CONTEXT? = analyze(element) { prepareContext(element) }

@OptIn(KtAllowAnalysisOnEdt::class)
internal fun <ELEMENT : KtElement, CONTEXT> KotlinApplicableToolWithContext<ELEMENT, CONTEXT>.prepareContextWithAnalyzeAllowEdt(
    element: ELEMENT,
): CONTEXT? = allowAnalysisOnEdt { prepareContextWithAnalyze(element) }
