// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ReflectionUtil
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.CREATE_BY_PATTERN_MAY_NOT_REFORMAT
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

abstract class QuickFixActionBase<out T : PsiElement>(element: T) : IntentionAction, Cloneable {
    @SafeFieldForPreview // not actually safe but will be properly patched in getFileModifierForPreview
    private val elementPointer = element.createSmartPointer()

    protected val element: T?
        get() = elementPointer.element

    open val isCrossLanguageFix: Boolean = false

    protected open fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile) = true

    final override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (isUnitTestMode()) {
            CREATE_BY_PATTERN_MAY_NOT_REFORMAT = true
        }
        try {
            val element = element ?: return false
            return element.isValid &&
                    !element.project.isDisposed &&
                    (file.manager.isInProject(file) || file is KtCodeFragment || (file is KtFile && file.isScript())) &&
                    (file is KtFile || isCrossLanguageFix) &&
                    isAvailableImpl(project, editor, file)
        } finally {
            CREATE_BY_PATTERN_MAY_NOT_REFORMAT = false
        }
    }

    override fun startInWriteAction() = true

    /**
     * This implementation clones current intention replacing [elementPointer]
     * field value with the pointer to the corresponding element
     * in the target file. It returns null if subclass has potentially unsafe fields not
     * marked with [@SafeFieldForPreview][SafeFieldForPreview].
     */
    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        // Check field safety in subclass
        if (super.getFileModifierForPreview(target) !== this) return null
        val oldElement: PsiElement? = element
        if (oldElement == null) return null
        val newElement = PsiTreeUtil.findSameElementInCopy(oldElement, target)
        val clone = try {
            super.clone() as QuickFixActionBase<*>
        } catch (e: CloneNotSupportedException) {
            throw InternalError(e) // should not happen as we implement Cloneable
        }
        if (!ReflectionUtil.setField(
            QuickFixActionBase::class.java, clone, SmartPsiElementPointer::class.java, "elementPointer",
            newElement.createSmartPointer()
        )) {
            return null
        }
        return clone
    }

    /**
     * Do not call this method, it's non-functional
     *
     * @throws CloneNotSupportedException always
     */
    @Throws(CloneNotSupportedException::class)
    override fun clone() = throw CloneNotSupportedException()
}