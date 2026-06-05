// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModNothing
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ContextProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.getElementContext
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

sealed class KotlinPsiUpdateModCommandAction<E : PsiElement, C : Any>(
    element: E?,
    elementClass: KClass<E>?,
) : PsiBasedModCommandAction<E>(element, elementClass?.java) {

    init {
        check(this !is LowPriorityAction && this !is HighPriorityAction) {
            "${javaClass.name}: neither LowPriorityAction nor HighPriorityAction have an effect on the modcommand action. " +
                    "Specify the priority explicitly in `getActionPresentation` method."
        }
    }

    /**
     * @see [PsiUpdateModCommandAction.perform]
     */
    @RequiresBackgroundThread
    final override fun perform(
        context: ActionContext,
        element: E,
    ): ModCommand = try {
        ModCommand.psiUpdate(context) { updater ->
            val e = updater.getWritable(element)
            val elementContext = getElementContext(context, e)
                                 ?: throw NoContextException()
            invoke(context, e, elementContext, updater)
        }
    } catch (_: NoContextException) {
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
    abstract fun invoke(
        actionContext: ActionContext,
        element: E,
        elementContext: C,
        updater: ModPsiUpdater,
    )

    protected abstract fun getElementContext(
        actionContext: ActionContext,
        element: E,
    ): C?

    /**
     * Returns `true` by default, adding the "Fix All" option to the presentation.
     * Override and return `false` to disable it.
     */
    protected open fun addFixAllOption(
        context: ActionContext,
        element: E,
    ): Boolean = true

    /**
     * Override to customize the presentation of the quick fix.
     */
    protected open fun getActionPresentation(
        context: ActionContext,
        element: E,
    ): Presentation? = Presentation.of(getFamilyName())

    /**
     * Override [getActionPresentation] instead of this method to customize the presentation of the quick fix.
     */
    final override fun getPresentation(
        actionContext: ActionContext,
        element: E,
    ): Presentation? {
        val presentation = getActionPresentation(actionContext, element) ?: return null
        return if (addFixAllOption(actionContext, element)) {
            presentation.withFixAllOption(this)
        } else {
            presentation
        }
    }

    abstract class ElementBased<E : PsiElement, C : Any>(
        element: E,
        @FileModifier.SafeFieldForPreview private val elementContext: C,
    ) : KotlinPsiUpdateModCommandAction<E, C>(element, null) {

        init {
            require(elementContext !is Unit) {
                """
                Use [ElementContextless] if you don't need an elementContext.
                See more in plugins/kotlin/docs/fir-ide/architecture/code-insights.md.
                """.trimIndent()
            }
        }

        final override fun getElementContext(
            actionContext: ActionContext,
            element: E,
        ): C = elementContext
    }

    abstract class ElementContextless<E : PsiElement>(
        element: E,
    ) : KotlinPsiUpdateModCommandAction<E, Unit>(element, null) {
        final override fun getElementContext(actionContext: ActionContext, element: E) {}

        final override fun invoke(
            actionContext: ActionContext,
            element: E,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            invoke(actionContext, element, updater)
        }

        @RequiresBackgroundThread
        abstract fun invoke(
            context: ActionContext,
            element: E,
            updater: ModPsiUpdater,
        )
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

    abstract class ClassContextless<E : KtElement>(
        elementClass: KClass<E>,
    ) : KotlinPsiUpdateModCommandAction<E, Unit>(null, elementClass) {
        final override fun getElementContext(actionContext: ActionContext, element: E) {}
    }
}