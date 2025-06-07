// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class RemoveRedundantSpreadOperatorFix(element: KtExpression) : PsiUpdateModCommandAction<KtExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.remove.redundant.star.text")

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val argument = element.getParentOfType<KtValueArgument>(strict = false) ?: return
        val spreadElement = argument.getSpreadElement() ?: return
        spreadElement.delete()
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val element = psiElement as? KtExpression ?: return emptyList()
            return listOf(
                RemoveRedundantSpreadOperatorFix(element).asIntention()
            )
        }
    }
}