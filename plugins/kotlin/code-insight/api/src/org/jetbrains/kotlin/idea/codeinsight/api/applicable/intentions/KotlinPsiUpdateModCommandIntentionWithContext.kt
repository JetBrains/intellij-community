// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A variant of [KotlinPsiUpdateModCommandIntention] that allows for passing an additional context through [prepareContext].
 */
abstract class KotlinPsiUpdateModCommandIntentionWithContext<ELEMENT : KtElement, CONTEXT>(
    clazz: KClass<ELEMENT>
) : KotlinPsiUpdateModCommandIntention<ELEMENT>(clazz) {
    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean = element is KtBlockExpression

    /**
     * @return The context of the intention, null if the intention is unavailable, only called when [isElementApplicable] returns true.
     */
    context(KtAnalysisSession)
    abstract fun prepareContext(element: ELEMENT): CONTEXT?

    final override fun getPresentation(context: ActionContext, element: ELEMENT): Presentation? {
        val analyzeContext = analyze(element) { prepareContext(element) } ?: return null
        return getPresentation(context, element, analyzeContext)
    }

    /**
     * [com.intellij.modcommand.PsiBasedModCommandAction.getPresentation] with additional [CONTEXT] variable.
     */
    protected open fun getPresentation(context: ActionContext, element: ELEMENT, analyzeContext: CONTEXT): Presentation? {
        return super.getPresentation(context, element)
    }

    final override fun invoke(context: ActionContext, element: ELEMENT, updater: ModPsiUpdater) {
        val analyzeContext = analyze(element) { prepareContext(element) } ?: return
        invoke(context, element, analyzeContext, updater)
    }

    /**
     * [com.intellij.modcommand.PsiUpdateModCommandAction.invoke] with additional [CONTEXT] variable.
     */
    abstract fun invoke(actionContext: ActionContext, element: ELEMENT, preparedContext: CONTEXT, updater: ModPsiUpdater)
}