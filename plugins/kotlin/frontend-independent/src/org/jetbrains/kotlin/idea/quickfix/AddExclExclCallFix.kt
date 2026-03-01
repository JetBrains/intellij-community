// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

class AddExclExclCallFix(
    element: PsiElement,
    private val fixImplicitReceiver: Boolean = false,
) : PsiUpdateModCommandAction<PsiElement>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.introduce.non.null.assertion.family")

    override fun getPresentation(context: ActionContext, element: PsiElement): Presentation =
        Presentation.of(KotlinBundle.message("fix.introduce.non.null.assertion.text", element.text))
            .withPriority(PriorityAction.Priority.LOW)

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        if (fixImplicitReceiver) {
            val exclExclExpression = if (element is KtCallableReferenceExpression) {
                psiFactory.createExpressionByPattern("this!!::$0", element.callableReference)
            } else {
                psiFactory.createExpressionByPattern("this!!.$0", element)
            }
            element.replace(exclExclExpression)
        } else {
            val parent = element.parent
            val operationToken = when (parent) {
                is KtBinaryExpression -> parent.operationToken
                is KtUnaryExpression -> parent.operationToken
                else -> null
            }
            when {
                operationToken in KtTokens.AUGMENTED_ASSIGNMENTS && parent is KtBinaryExpression && element == parent.left -> {
                    val right = parent.right ?: return
                    val newOperationToken = when (operationToken) {
                        KtTokens.PLUSEQ -> KtTokens.PLUS
                        KtTokens.MINUSEQ -> KtTokens.MINUS
                        KtTokens.MULTEQ -> KtTokens.MUL
                        KtTokens.PERCEQ -> KtTokens.PERC
                        KtTokens.DIVEQ -> KtTokens.DIV
                        else -> return
                    }
                    val newExpression = psiFactory.createExpressionByPattern(
                        "$0 = $1!! ${newOperationToken.value} $2", element, element, right
                    )
                    parent.replace(newExpression)
                }

                (operationToken == KtTokens.PLUSPLUS || operationToken == KtTokens.MINUSMINUS) && parent is KtUnaryExpression -> {
                    val newOperationToken = if (operationToken == KtTokens.PLUSPLUS) KtTokens.PLUS else KtTokens.MINUS
                    val newExpression = psiFactory.createExpressionByPattern(
                        "$0 = $1!! ${newOperationToken.value} 1", element, element
                    )
                    parent.replace(newExpression)
                }

                else -> element.replace(psiFactory.createExpressionByPattern("$0!!", element))
            }
        }
    }
}