// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class AddWhenElseBranchFix(element: KtWhenExpression) : KotlinPsiOnlyQuickFixAction<KtWhenExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.add.else.branch.when")
    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element?.closeBrace != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val whenCloseBrace = element.closeBrace ?: return
        val entry = KtPsiFactory(file).createWhenEntry("else -> {}")
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
