// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.dropEnclosingParenthesesIfPossible
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtBinaryExpression

class RemoveUselessElvisFix(element: KtBinaryExpression) : PsiUpdateModCommandAction<KtBinaryExpression>(element), CleanupFix.ModCommand {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.useless.elvis.operator")

    override fun invoke(context: ActionContext, element: KtBinaryExpression, updater: ModPsiUpdater) {
        element.replaced(element.left!!).dropEnclosingParenthesesIfPossible()
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement as? KtBinaryExpression ?: return emptyList()
            return listOf(
                RemoveUselessElvisFix(expression).asIntention()
            )
        }
    }
}