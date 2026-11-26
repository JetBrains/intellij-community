// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.dropEnclosingParenthesesIfPossible
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtBinaryExpression

class RemoveUselessElvisFix(
    element: KtBinaryExpression,
    private val replacementSide: ReplacementSide = ReplacementSide.LEFT,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtBinaryExpression>(element),
    CleanupFix.ModCommand {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.elvis.operator")

    override fun invoke(context: ActionContext, element: KtBinaryExpression, updater: ModPsiUpdater) {
        val replacement = when (replacementSide) {
            ReplacementSide.LEFT -> element.left
            ReplacementSide.RIGHT -> element.right
        } ?: return
        val commentSaver = CommentSaver(element)
        val result = element.replaced(replacement).dropEnclosingParenthesesIfPossible()
        commentSaver.restore(result)
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        val replaceWithRightFactory: QuickFixesPsiBasedFactory<PsiElement> = object : QuickFixesPsiBasedFactory<PsiElement>(
            PsiElement::class,
            PsiElementSuitabilityCheckers.ALWAYS_SUITABLE,
        ) {
            override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> = createQuickFix(psiElement, ReplacementSide.RIGHT)
        }

        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            return createQuickFix(psiElement, ReplacementSide.LEFT)
        }

        private fun createQuickFix(psiElement: PsiElement, replacementSide: ReplacementSide): List<IntentionAction> {
            val expression = psiElement as? KtBinaryExpression ?: return emptyList()
            return listOf(
                RemoveUselessElvisFix(expression, replacementSide).asIntention()
            )
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtBinaryExpression): Presentation {
        return Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
    }

    enum class ReplacementSide {
        LEFT,
        RIGHT,
    }
}
