// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

sealed class AddElseBranchFix<T : KtExpression>(element: T) : PsiUpdateModCommandAction<T>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.else.branch.when")

    abstract override fun getPresentation(context: ActionContext, element: T): Presentation?
    abstract override fun invoke(context: ActionContext, element: T, updater: ModPsiUpdater)
}

class AddWhenElseBranchFix(element: KtWhenExpression) : AddElseBranchFix<KtWhenExpression>(element), LowPriorityAction {
    override fun getPresentation(context: ActionContext, element: KtWhenExpression): Presentation? =
        Presentation.of(familyName).takeIf { element.closeBrace != null }

    override fun invoke(
        context: ActionContext,
        element: KtWhenExpression,
        updater: ModPsiUpdater,
    ) {
        val whenCloseBrace = element.closeBrace ?: return
        val entry = KtPsiFactory(context.project).createWhenEntry("else -> {}")
        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element.addBefore(entry, whenCloseBrace))?.endOffset?.let { offset ->
            updater.moveCaretTo(offset - 1)
        }
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            return listOfNotNull(psiElement.getNonStrictParentOfType<KtWhenExpression>()?.let { AddWhenElseBranchFix(it).asIntention() })
        }
    }
}

class AddIfElseBranchFix(element: KtIfExpression) : AddElseBranchFix<KtIfExpression>(element) {
    override fun getPresentation(context: ActionContext, element: KtIfExpression): Presentation? {
        return Presentation.of(familyName).takeIf {
            element.`else` == null &&
                    element.condition != null &&
                    element.children.firstOrNull { it.elementType == KtNodeTypes.THEN }?.firstChild !is PsiErrorElement
        }
    }

    override fun invoke(
        context: ActionContext,
        element: KtIfExpression,
        updater: ModPsiUpdater,
    ) {
        val withBraces = element.then is KtBlockExpression
        val psiFactory = KtPsiFactory(context.project)
        val newIf = psiFactory.createExpression(
            if (withBraces) {
                "if (true) {} else { TODO() }"
            } else {
                "if (true) 2 else TODO()"
            }
        ) as KtIfExpression

        element.addRange(newIf.then?.parent?.nextSibling, newIf.`else`?.parent)

        if (withBraces) {
            val todoStatement = (element.`else` as KtBlockExpression).statements.single()
            updater.moveCaretTo(todoStatement)
            todoStatement.containingFile
                .fileDocument
                .deleteString(todoStatement.startOffset, todoStatement.endOffset)
        } else {
            element.`else`?.textRange?.let {
                updater.moveCaretTo(it.startOffset)
                updater.select(it)
            }
        }
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            return listOfNotNull(
                ((psiElement as? KtIfExpression) ?: (psiElement.parent as? KtIfExpression))?.let { AddIfElseBranchFix(it).asIntention() }
            )
        }
    }
}
