// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
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
                  "Specify the priority explicitly in `getPresentation` method."
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

    abstract class ElementBased<E : PsiElement, C : Any>(
        element: E,
        @FileModifier.SafeFieldForPreview private val elementContext: C,
    ) : KotlinPsiUpdateModCommandAction<E, C>(element, null) {

        init {
            require(elementContext !is Unit) {
                """
                Use [PsiUpdateModCommandAction] if you don't need an elementContext.
                See more in plugins/kotlin/docs/fir-ide/architecture/code-insights.md.
                """.trimIndent()
            }
        }

        final override fun getElementContext(
            actionContext: ActionContext,
            element: E,
        ): C = elementContext
    }

    abstract class ClassBased<E : KtElement, C : Any>(
        elementClass: KClass<E>,
    ) : KotlinPsiUpdateModCommandAction<E, C>(null, elementClass),
        ContextProvider<E, C> {

        @OptIn(KaAllowAnalysisOnEdt::class)
        final override fun getElementContext(
            actionContext: ActionContext,
            element: E,
        ): C? = allowAnalysisOnEdt { // TODO: remove this workaround when IJPL-193738 is fixed.
            getElementContext(element)
        }
    }
}