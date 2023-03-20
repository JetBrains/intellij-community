// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.util.executeEnterHandler
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

sealed class AddElseBranchFix<T : KtExpression>(element: T) : KotlinPsiOnlyQuickFixAction<T>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.add.else.branch.when")
    override fun getText() = familyName

    abstract override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean

    abstract override fun invoke(project: Project, editor: Editor?, file: KtFile)
}

class AddWhenElseBranchFix(element: KtWhenExpression) : AddElseBranchFix<KtWhenExpression>(element) {
    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element?.closeBrace != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val whenCloseBrace = element.closeBrace ?: return
        val entry = KtPsiFactory(project).createWhenEntry("else -> {}")
        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element.addBefore(entry, whenCloseBrace))?.endOffset?.let { offset ->
            editor?.caretModel?.moveToOffset(offset - 1)
        }
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            return listOfNotNull(psiElement.getNonStrictParentOfType<KtWhenExpression>()?.let(::AddWhenElseBranchFix))
        }
    }
}

class AddIfElseBranchFix(element: KtIfExpression) : AddElseBranchFix<KtIfExpression>(element) {
    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val ktIfExpression = element ?: return false
        return ktIfExpression.`else` == null &&
                ktIfExpression.condition != null &&
                ktIfExpression.children.firstOrNull { it.elementType == KtNodeTypes.THEN }?.firstChild !is PsiErrorElement
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val withBraces = element.then is KtBlockExpression
        val psiFactory = KtPsiFactory(project)
        val newIf = psiFactory.createExpression(
            if (withBraces) {
                "if (true) {} else {}"
            } else {
                "if (true) 2 else TODO()"
            }
        ) as KtIfExpression

        element.addRange(newIf.then?.parent?.nextSibling, newIf.`else`?.parent)
        editor?.caretModel?.currentCaret?.let { caret ->
            if (withBraces) {
                caret.moveToOffset(element.endOffset - 1)
                val documentManager = PsiDocumentManager.getInstance(project)
                documentManager.getDocument(element.containingFile)?.let { doc ->
                    documentManager.doPostponedOperationsAndUnblockDocument(doc)
                    editor.executeEnterHandler()
                }
            } else {
                element.`else`?.textRange?.let {
                    caret.moveToOffset(it.startOffset)
                    caret.setSelection(it.startOffset, it.endOffset)
                }
            }
        }
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            return listOfNotNull(psiElement.parent?.safeAs<KtIfExpression>()?.let(::AddIfElseBranchFix))
        }
    }
}
