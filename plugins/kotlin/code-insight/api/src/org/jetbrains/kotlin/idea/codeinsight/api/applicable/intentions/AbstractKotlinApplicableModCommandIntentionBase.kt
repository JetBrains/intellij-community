// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableToElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntentionBase<ELEMENT : KtElement>(
    private val clazz: KClass<ELEMENT>
) : PsiUpdateModCommandAction<ELEMENT>(clazz.java), KotlinApplicableToolBase<ELEMENT> {

    protected abstract fun getActionName(element: ELEMENT): @IntentionName String

    override fun isElementApplicable(element: ELEMENT, context: ActionContext): Boolean {
        if (!isApplicableByPsi(element)) return false

        val applicabilityRanges = getApplicabilityRange().getApplicabilityRanges(element)
        if (applicabilityRanges.isEmpty()) return false
        // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
        val relativeCaretOffset = context.offset - element.startOffset
        return applicabilityRanges.any { it.containsOffset(relativeCaretOffset) }
    }

    /**
     * Checks the intention's applicability based on [isApplicableByPsi] and [KotlinApplicabilityRange].
     *
     * To be invoked on a background thread only.
     *
     * @param element is a non-physical [PsiElement]
     */
    open fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean = isApplicableToElement(element, caretOffset)

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

    override fun isApplicableByPsi(element: ELEMENT): Boolean = true

    override fun getPresentation(context: ActionContext, element: ELEMENT): Presentation? =
        Presentation.of(getActionName(element))
}
