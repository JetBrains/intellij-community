// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class ExclExclCallFix(psiElement: PsiElement) : KotlinPsiOnlyQuickFixAction<PsiElement>(psiElement) {
    override fun getFamilyName(): String = text
}

class RemoveExclExclCallFix(
    psiElement: PsiElement
) : ExclExclCallFix(psiElement), CleanupFix, HighPriorityAction, IntentionActionWithFixAllOption {
    override fun getText(): String = KotlinBundle.message("fix.remove.non.null.assertion")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val postfixExpression = element as? KtPostfixExpression ?: return
        val baseExpression = postfixExpression.baseExpression ?: return
        postfixExpression.replace(baseExpression)
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val postfixExpression = psiElement.getNonStrictParentOfType<KtPostfixExpression>() ?: return emptyList()
            return listOfNotNull(RemoveExclExclCallFix(postfixExpression))
        }
    }
}