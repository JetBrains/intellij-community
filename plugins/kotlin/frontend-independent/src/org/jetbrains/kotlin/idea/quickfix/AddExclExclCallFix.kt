// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class AddExclExclCallFix(psiElement: PsiElement, val fixImplicitReceiver: Boolean = false) : ExclExclCallFix(psiElement),
    LowPriorityAction {

    override fun getText() = KotlinBundle.message("fix.introduce.non.null.assertion")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        val modifiedExpression = element ?: return
        val psiFactory = KtPsiFactory(project)
        if (fixImplicitReceiver) {
            val exclExclExpression = if (modifiedExpression is KtCallableReferenceExpression) {
                psiFactory.createExpressionByPattern("this!!::$0", modifiedExpression.callableReference)
            } else {
                psiFactory.createExpressionByPattern("this!!.$0", modifiedExpression)
            }
            modifiedExpression.replace(exclExclExpression)
        } else {
            val parent = modifiedExpression.parent
            val operationToken = when (parent) {
                is KtBinaryExpression -> parent.operationToken
                is KtUnaryExpression -> parent.operationToken
                else -> null
            }
            when {
                operationToken in KtTokens.AUGMENTED_ASSIGNMENTS && parent is KtBinaryExpression -> {
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
                        "$0 = $1!! ${newOperationToken.value} $2", modifiedExpression, modifiedExpression, right
                    )
                    parent.replace(newExpression)
                }
                (operationToken == KtTokens.PLUSPLUS || operationToken == KtTokens.MINUSMINUS) && parent is KtUnaryExpression -> {
                    val newOperationToken = if (operationToken == KtTokens.PLUSPLUS) KtTokens.PLUS else KtTokens.MINUS
                    val newExpression = psiFactory.createExpressionByPattern(
                        "$0 = $1!! ${newOperationToken.value} 1", modifiedExpression, modifiedExpression
                    )
                    parent.replace(newExpression)
                }
                else -> modifiedExpression.replace(psiFactory.createExpressionByPattern("$0!!", modifiedExpression))
            }
        }
    }
}