// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.AddBracesUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.getControlFlowElementDescription
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@Internal
@IntellijInternalApi
class AddBracesIntention : KotlinPsiUpdateModCommandAction.Simple<KtElement>(KtElement::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.braces")

    override fun isElementApplicable(element: KtElement, context: ActionContext): Boolean {
        val expression = element.getTargetExpression(context.offset) ?: return false
        // it stops further element look-up at `com.intellij.modcommand.PsiBasedModCommandAction.getElement`
        if (expression is KtBlockExpression) return true

        return when (val parent = expression.parent) {
            is KtContainerNode -> parent.getControlFlowElementDescription() != null
            is KtWhenEntry -> true
            else -> false
        }
    }

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
        return super.stopSearchAt(element, context)
    }

    override fun getPresentation(context: ActionContext, element: KtElement): Presentation? {
        val expression = element.getTargetExpression(context.offset) ?: return null
        if (expression is KtBlockExpression) return null

        return when (val parent = expression.parent) {
            is KtContainerNode -> {
                val description = parent.getControlFlowElementDescription() ?: return null
                Presentation.of(KotlinBundle.message("add.braces.to.0.statement", description))
            }
            is KtWhenEntry -> {
                Presentation.of(KotlinBundle.message("add.braces.to.when.entry"))
            }
            else -> {
                null
            }
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtElement, elementContext: Unit, updater: ModPsiUpdater) {
        val expression = element.getTargetExpression(updater.caretOffset) ?: return
        AddBracesUtils.addBraces(element, expression)
    }

    private fun KtElement.getTargetExpression(caretLocation: Int): KtExpression? {
        return when (this) {
            is KtIfExpression -> {
                val thenExpr = then ?: return null
                val elseExpr = `else`
                if (elseExpr != null && (caretLocation >= (elseKeyword?.startOffset ?: return null))) {
                    elseExpr
                } else {
                    thenExpr
                }
            }

            is KtLoopExpression -> body
            is KtWhenEntry -> expression
            else -> null
        }
    }
}
