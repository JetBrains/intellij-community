// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

class KotlinDeleteCompletionCommand : ApplicableCompletionCommand(), DumbAware {
    private var _highlightInfo: HighlightInfoLookup? = null
    override val name: String
        get() = "Delete element"
    override val i18nName: @Nls String
        get() = ActionsBundle.message("action.EditorDelete.text")
    override val icon: Icon?
        get() = null
    override val highlightInfo: HighlightInfoLookup?
        get() = _highlightInfo

    override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
        val element = getContext(offset, psiFile) ?: return false
        var psiElement = PsiTreeUtil.getParentOfType(element, KtExpression::class.java, KtNamedDeclaration::class.java) ?: return false
        val hasTheSameOffset = psiElement.textRange.endOffset == offset
        if (!hasTheSameOffset) return false
        psiElement = getTopWithTheSameOffset(psiElement, offset)
        _highlightInfo = HighlightInfoLookup(psiElement.textRange, EditorColors.DELETED_TEXT_ATTRIBUTES, 0)
        return true
    }

    override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val element = getContext(offset, psiFile) ?: return
        var psiElement = PsiTreeUtil.getParentOfType(element, KtExpression::class.java, KtNamedDeclaration::class.java) ?: return
        psiElement = getTopWithTheSameOffset(psiElement, offset)
        WriteCommandAction.runWriteCommandAction(psiFile.project, null, null, {
            val parent: SmartPsiElementPointer<PsiElement?> = SmartPointerManager.createPointer(psiElement.parent ?: psiFile)
            psiElement.delete()
            PsiDocumentManager.getInstance(psiFile.project).commitDocument(psiFile.fileDocument)
            parent.element?.let {
                ReformatCodeProcessor(psiFile, arrayOf(it.textRange)).run()
            }
        }, psiFile)
    }

    private fun getTopWithTheSameOffset(psiElement: KtExpression, offset: Int): KtExpression {
        var psiElement1 = psiElement
        var curElement = psiElement1
        while (curElement.textRange.endOffset == offset) {
            psiElement1 = curElement
            curElement = PsiTreeUtil.getParentOfType(curElement, KtExpression::class.java, KtNamedDeclaration::class.java) ?: break
        }
        return psiElement1
    }
}