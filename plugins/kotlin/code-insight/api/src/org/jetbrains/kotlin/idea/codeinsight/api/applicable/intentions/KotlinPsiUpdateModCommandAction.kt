// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ContextProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.getElementContext
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

sealed class KotlinPsiUpdateModCommandAction<E : PsiElement, C : Any> private constructor(
    element: E?,
    elementClass: KClass<E>?,
) : PsiBasedModCommandAction<E>(element, elementClass?.java) {

    /**
     * @see [PsiUpdateModCommandAction.perform]
     */
    @RequiresBackgroundThread
    final override fun perform(
        context: ActionContext,
        element: E,
    ): ModCommand = try {
        ModCommand.psiUpdate(element) { e, updater ->
            val elementContext = getElementContext(context, e)
                                 ?: throw NoContextException()
            invoke(context, e, elementContext, updater)
        }
    }
    catch (e: NoContextException) {
        ModNothing.NOTHING
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: RuntimeException) {
        throw RuntimeException("When launching $familyName (${javaClass.name})", e)
    }

    private class NoContextException : RuntimeException()

    /**
     * @see [PsiUpdateModCommandAction.invoke]
     */
    @RequiresBackgroundThread
    protected abstract fun invoke(
        actionContext: ActionContext,
        element: E,
        elementContext: C,
        updater: ModPsiUpdater,
    )

    protected abstract fun getElementContext(
        actionContext: ActionContext,
        element: E,
    ): C?

    protected open fun getActionName(
        actionContext: ActionContext,
        element: E,
        elementContext: C,
    ): @IntentionName String = familyName

    final override fun getPresentation(
        context: ActionContext,
        element: E,
    ): Presentation? = getElementContext(context, element)
        ?.let { getActionName(context, element, it) }
        ?.let { Presentation.of(it) }

    abstract class ElementBased<E : PsiElement, C : Any>(
        element: E,
        @FileModifier.SafeFieldForPreview private val elementContext: C,
    ) : KotlinPsiUpdateModCommandAction<E, C>(element, null) {

        final override fun getElementContext(
            actionContext: ActionContext,
            element: E,
        ): C = elementContext
    }

    abstract class ClassBased<E : KtElement, C : Any>(
        elementClass: KClass<E>,
    ) : KotlinPsiUpdateModCommandAction<E, C>(null, elementClass),
        ContextProvider<E, C> {

        final override fun getElementContext(
            actionContext: ActionContext,
            element: E,
        ): C? = getElementContext(element)
    }
}