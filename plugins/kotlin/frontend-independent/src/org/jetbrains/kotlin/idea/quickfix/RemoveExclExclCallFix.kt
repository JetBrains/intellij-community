// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RemoveExclExclCallFix(
    element: PsiElement,
) : PsiUpdateModCommandAction<PsiElement>(element), CleanupFix.ModCommand {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.remove.non.null.assertion")

    override fun getPresentation(context: ActionContext, element: PsiElement): Presentation =
        Presentation.of(familyName)
            .withPriority(PriorityAction.Priority.HIGH)
            .withFixAllOption(this)

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater): Unit = invoke(element)

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        fun invoke(element: PsiElement) {
            val postfixExpression = element as? KtPostfixExpression ?: return
            val baseExpression = postfixExpression.baseExpression ?: return
            postfixExpression.replace(baseExpression)
        }

        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val postfixExpression = psiElement.getNonStrictParentOfType<KtPostfixExpression>() ?: return emptyList()
            return listOfNotNull(RemoveExclExclCallFix(postfixExpression).asIntention())
        }
    }
}