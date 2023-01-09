// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConfusingExpressionInWhenBranchFix(element: KtExpression) : KotlinPsiOnlyQuickFixAction<KtExpression>(element) {

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.safeAs<KtExpression>() ?: return emptyList()
            return listOf(ConfusingExpressionInWhenBranchFix(expression))
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val wrapped = KtPsiFactory(project).createExpressionByPattern("($0)", expression)
        expression.replace(wrapped)
    }

    override fun getText(): String {
        return KotlinBundle.message("wrap.expression.in.parentheses")
    }

    override fun getFamilyName(): String = text
}