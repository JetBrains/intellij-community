// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

abstract class KotlinModCommandAction<E : PsiElement, C : KotlinModCommandAction.ElementContext>(
    element: E,
    @FileModifier.SafeFieldForPreview private val elementContext: C,
) : PsiBasedModCommandAction<E>(element) {

    interface ElementContext {

        fun isValid(context: ActionContext): Boolean = true
    }

    /**
     * @see [PsiUpdateModCommandAction.perform]
     */
    @RequiresBackgroundThread
    final override fun perform(
        context: ActionContext,
        element: E,
    ): ModCommand = try {
        val input = getElementContext(context)

        if (input != null) {
            ModCommand.psiUpdate(element) { e, updater ->
                invoke(context, e, input, updater)
            }
        } else {
            ModNothing.NOTHING
        }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: RuntimeException) {
        throw RuntimeException("When launching $familyName (${javaClass.name})", e)
    }

    /**
     * @see [PsiUpdateModCommandAction.invoke]
     */
    @RequiresBackgroundThread
    protected abstract fun invoke(
        context: ActionContext,
        element: E,
        elementContext: C,
        updater: ModPsiUpdater,
    )

    protected open fun getActionName(
        context: ActionContext,
        element: E,
        elementContext: C,
    ): @IntentionName String = familyName

    override fun getPresentation(
        context: ActionContext,
        element: E,
    ): Presentation? = getElementContext(context)
        ?.let { getActionName(context, element, it) }
        ?.let { Presentation.of(it) }

    private fun getElementContext(context: ActionContext): C? = elementContext
        .takeIf { it.isValid(context) }
}