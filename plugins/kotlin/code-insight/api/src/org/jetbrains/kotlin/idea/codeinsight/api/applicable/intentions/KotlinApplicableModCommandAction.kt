// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ApplicableRangesProvider
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A Kotlin intention base class for intentions that update some PSI using [ModCommand] APIs.
 */
abstract class KotlinApplicableModCommandAction<E : KtElement, C : Any>(
    elementClass: KClass<E>,
) : KotlinPsiUpdateModCommandAction.ClassBased<E, C>(elementClass),
    ApplicableRangesProvider<E> {

    override fun stopSearchAt(
        element: PsiElement,
        context: ActionContext,
    ): Boolean = element is KtBlockExpression

    final override fun isElementApplicable(
        element: E,
        context: ActionContext,
    ): Boolean {
        if (!isApplicableByPsi(element)) return false

        // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
        val relativeCaretOffset = context.offset - element.startOffset
        val ranges = KtAnalysisAllowanceManager.forbidAnalysisInside("getApplicabilityRanges") {
            getApplicableRanges(element)
        }
        if (!ranges.any { it.containsOffset(relativeCaretOffset) }) return false

        return getElementContext(context, element) != null
    }
}