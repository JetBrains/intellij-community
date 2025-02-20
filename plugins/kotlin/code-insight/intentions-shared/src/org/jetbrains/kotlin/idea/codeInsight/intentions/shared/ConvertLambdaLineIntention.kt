// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSemicolon
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

internal sealed class ConvertLambdaLineIntention(private val toMultiLine: Boolean) :
    KotlinApplicableModCommandAction<KtLambdaExpression, Unit>(KtLambdaExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("intention.convert.lambda.line", 1.takeIf { toMultiLine } ?: 0)

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        val functionLiteral = element.functionLiteral
        val body = functionLiteral.bodyBlockExpression ?: return false
        val startLine = functionLiteral.getLineNumber(start = true)
        val endLine = functionLiteral.getLineNumber(start = false)
        return if (toMultiLine) {
            startLine == endLine
        } else {
            if (startLine == endLine) return false
            val allChildren = body.allChildren
            if (allChildren.any { it is PsiComment && it.node.elementType == KtTokens.EOL_COMMENT }) return false
            val first = allChildren.first?.getNextSiblingIgnoringWhitespace(withItself = true) ?: return true
            val last = allChildren.last?.getPrevSiblingIgnoringWhitespace(withItself = true)
            first.getLineNumber(start = true) == last?.getLineNumber(start = false)
        }
    }

    override fun KaSession.prepareContext(element: KtLambdaExpression): Unit = Unit

    override fun invoke(
        actionContext: ActionContext,
        element: KtLambdaExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val functionLiteral = element.functionLiteral
        val body = functionLiteral.bodyBlockExpression ?: return
        val psiFactory = KtPsiFactory(element.project)
        if (toMultiLine) {
            body.allChildren.forEach {
                if (it.node.elementType == KtTokens.SEMICOLON) {
                    body.addAfter(psiFactory.createNewLine(), it)
                    if (isRedundantSemicolon(it)) it.delete()
                }
            }
        }
        val bodyText = body.text
        val startLineBreak = if (toMultiLine) "\n" else ""
        val endLineBreak = if (toMultiLine && bodyText != "") "\n" else ""
        element.replace(
            psiFactory.createLambdaExpression(
                functionLiteral.valueParameters.joinToString { it.text },
                "$startLineBreak$bodyText$endLineBreak"
            )
        )
    }
}

internal class ConvertLambdaToMultiLineIntention : ConvertLambdaLineIntention(toMultiLine = true)

internal class ConvertLambdaToSingleLineIntention : ConvertLambdaLineIntention(toMultiLine = false)
