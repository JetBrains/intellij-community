// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinModCommandAction
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A Kotlin intention base class for intentions that update some PSI using [ModCommand] APIs.
 */
abstract class KotlinApplicableModCommandAction<E : KtElement, C : Any>(
    elementClass: KClass<E>,
) : KotlinModCommandAction.ClassBased<E, C>(elementClass) {

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
        val ranges = getApplicabilityRange()
            .getApplicabilityRanges(element)
        if (!ranges.any { it.containsOffset(relativeCaretOffset) }) return false

        return getElementContext(context, element) != null
    }

    /**
     * The [KotlinApplicabilityRange] determines whether the tool is available in a range *after* [isApplicableByPsi] has been checked.
     *
     * Configuration of the applicability range might be as simple as choosing an existing one from `ApplicabilityRanges`.
     */
    // todo fun getApplicableRanges(element: E): List<E>
    protected abstract fun getApplicabilityRange(): KotlinApplicabilityRange<E>

    /**
     * Whether this tool is applicable to [element] by PSI only. May not use the Analysis API due to performance concerns.
     */
    protected open fun isApplicableByPsi(element: E): Boolean = true
}