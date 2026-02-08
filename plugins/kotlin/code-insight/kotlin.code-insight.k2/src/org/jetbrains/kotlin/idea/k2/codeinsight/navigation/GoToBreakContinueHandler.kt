// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.codeinsight.utils.findRelevantLoopForExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression

class GoToBreakContinueHandler : GotoDeclarationHandlerBase() {

    override fun getGotoDeclarationTarget(element: PsiElement?, editor: Editor?): PsiElement? {
        if (element == null) return null

        return when (element.elementType) {
            KtTokens.BREAK_KEYWORD -> {
                val breakExpression = element.parent as? KtBreakExpression ?: return null
                findBreakTarget(breakExpression)
            }

            KtTokens.CONTINUE_KEYWORD -> {
                val continueExpression = element.parent as? KtContinueExpression ?: return null
                findRelevantLoopForExpression(continueExpression)
            }

            else -> null
        }
    }

    private fun findBreakTarget(breakExpression: KtBreakExpression): PsiElement? {
        val exitedExpression = findRelevantLoopForExpression(breakExpression)
            ?: return null

        val targetStatement = exitedExpression.parent as? KtLabeledExpression ?: exitedExpression

        var nextSibling = targetStatement.nextSibling
        while (nextSibling != null && nextSibling !is KtExpression) {
            nextSibling = nextSibling.nextSibling
        }

        return nextSibling
            ?: targetStatement.nextSibling
            ?: targetStatement.lastChild
    }
}