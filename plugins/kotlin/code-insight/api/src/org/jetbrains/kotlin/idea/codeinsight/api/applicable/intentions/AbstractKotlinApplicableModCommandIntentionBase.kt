// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableToElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.containsInside
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntentionBase<ELEMENT : KtElement>(private val clazz: KClass<ELEMENT>) :
    PsiUpdateModCommandAction<ELEMENT>(clazz.java),
    KotlinApplicableToolBase<ELEMENT> {

    protected abstract fun getActionName(element: ELEMENT): @IntentionName String
    
    /**
     * Checks the intention's applicability based on [isApplicableByPsi] and [KotlinApplicabilityRange].
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

    fun getTarget(offset: Int, file: PsiFile): ELEMENT? {
        if (!checkFile(file)) return null

        val leaf1 = file.findElementAt(offset)
        val leaf2 = file.findElementAt(offset - 1)
        val commonParent = if (leaf1 != null && leaf2 != null) PsiTreeUtil.findCommonParent(leaf1, leaf2) else null

        var elementsToCheck: Sequence<PsiElement> = emptySequence()
        if (leaf1 != null) elementsToCheck += leaf1.parentsWithSelf.takeWhile { it != commonParent }
        if (leaf2 != null) elementsToCheck += leaf2.parentsWithSelf.takeWhile { it != commonParent }
        if (commonParent != null && commonParent !is PsiFile) elementsToCheck += commonParent.parentsWithSelf

        for (element in elementsToCheck) {
            @Suppress("UNCHECKED_CAST")
            if (clazz.isInstance(element)) {
                ProgressManager.checkCanceled()
                if (isApplicableTo(element as ELEMENT, offset)) {
                    return element
                }
                if (visitTargetTypeOnlyOnce()) {
                    return null
                }
            }
            if (element.textRange.containsInside(offset) && skipProcessingFurtherElementsAfter(element)) break
        }
        return null
    }

    fun getTarget(editor: Editor, file: PsiFile): ELEMENT? {
        if (isKotlinOnlyIntention && file !is KtFile) return null

        val offset = editor.caretModel.offset
        return getTarget(offset, file)
    }

    /** Whether to skip looking for targets after having processed the given element, which contains the cursor. */
    protected open fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean = element is KtBlockExpression

    protected open fun visitTargetTypeOnlyOnce(): Boolean = false

    override fun getPresentation(context: ActionContext, element: ELEMENT): Presentation? {
        if (!isApplicableTo(element, context.offset)) return null
        return Presentation.of(getActionName(element))
    }
}
