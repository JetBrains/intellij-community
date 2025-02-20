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
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RemoveUselessCastFix(element: KtBinaryExpressionWithTypeRHS) : PsiUpdateModCommandAction<KtBinaryExpressionWithTypeRHS>(element),
                                                                     CleanupFix.ModCommand {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.useless.cast")

    override fun invoke(context: ActionContext, element: KtBinaryExpressionWithTypeRHS, updater: ModPsiUpdater) {
        invoke(element)
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        operator fun invoke(element: KtBinaryExpressionWithTypeRHS): KtExpression = element.replaced(element.left).dropEnclosingParenthesesIfPossible()

        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.getNonStrictParentOfType<KtBinaryExpressionWithTypeRHS>() ?: return emptyList()
            return listOf(
                RemoveUselessCastFix(expression).asIntention()
            )
        }
    }
}

