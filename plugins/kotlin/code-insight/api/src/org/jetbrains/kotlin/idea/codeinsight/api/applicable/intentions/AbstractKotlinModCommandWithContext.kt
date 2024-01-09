// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableToElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.prepareContextWithAnalyze
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinModCommandWithContext<ELEMENT : KtElement, CONTEXT>(
    clazz: KClass<ELEMENT>
) : PsiUpdateModCommandAction<ELEMENT>(clazz.java),
    KotlinApplicableToolWithContext<ELEMENT, CONTEXT> {

    /**
     * Checks the intention's applicability based on [isApplicableByPsi] and [KotlinApplicabilityRange].
     *
     * To be invoked on a background thread only.
     *
     * @param element is a non-physical [PsiElement]
    */
    override fun isElementApplicable(element: ELEMENT, context: ActionContext): Boolean {
        return isApplicableToElement(element, context.offset) && analyze(element) { isApplicableByAnalyze(element) }
    }

    /*
     * Checks if the element is applicable performing analysis.
     *
     * To be invoked on a background thread only.
     *
     * @param element is a non-physical [PsiElement]
     */
    context(KtAnalysisSession)
    protected open fun isApplicableByAnalyze(element: ELEMENT): Boolean = true

    protected open val isKotlinOnlyIntention: Boolean = true

    /**
     * Override if the action should be available on library sources.
     * It means that it won't modify the code of the current file e.g., it implements the interface in project code or change some settings
     */
    protected open fun checkFile(file: PsiFile): Boolean {
        return BaseIntentionAction.canModify(file)
    }

    /** Whether to skip looking for targets after having processed the given element, which contains the cursor. */
    protected open fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean = element is KtBlockExpression

    protected open fun visitTargetTypeOnlyOnce(): Boolean = false

    /**
     * To be invoked on a background thread only.
     *
     * @param element is a non-physical [PsiElement]
     */
    override fun getPresentation(context: ActionContext, element: ELEMENT): Presentation? {
        val analysisContext = prepareContextWithAnalyze(element) ?: return null
        return Presentation.of(getActionName(element, analysisContext))
    }

    /**
     * To be invoked on a background thread only.
     *
     * @param element is a non-physical [PsiElement]
     */
    final override fun invoke(context: ActionContext, element: ELEMENT, updater: ModPsiUpdater) {
        val analyzeContext = analyze(element) { invokeContext(element) } ?: return
        apply(element, AnalysisActionContext(analyzeContext, context), updater)
    }

    context(KtAnalysisSession)
    open fun invokeContext(element: ELEMENT): CONTEXT? = prepareContext(element)

    /**
     * Applies a fix to [element] using information from [context]. [apply] should not use the Analysis API due to performance concerns, as
     * [apply] is usually executed on the EDT. Any information that needs to come from the Analysis API should be supplied via
     * [prepareContext]. [apply] is executed in a write action if [element] is physical and [shouldApplyInWriteAction] returns `true`.
     *
     * @param element a non-physical PSI
     *
     */
    open fun apply(element: ELEMENT, context: AnalysisActionContext<CONTEXT>, updater: ModPsiUpdater) {
        apply(element, context.analyzeContext, context.actionContext.project, editor = null)
    }

    final override fun apply(element: ELEMENT, context: CONTEXT, project: Project, editor: Editor?) {
        throw UnsupportedOperationException("apply(ELEMENT, CONTEXT, Project, Editor?) should not be invoked")
    }

}

data class AnalysisActionContext<C>(val analyzeContext: C, val actionContext: ActionContext)