// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

class JoinToStringTemplateHandler : JoinRawLinesHandlerDelegate {
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return -1

        if (start == 0) return -1
        val c = document.charsSequence[start]
        val index = if (c == '\n') start - 1 else start

        val plus = file.findElementAt(index)?.takeIf { it.node?.elementType == KtTokens.PLUS } ?: return -1
        var binaryExpr = plus.parents.match(KtOperationReferenceExpression::class, last = KtBinaryExpression::class)
            ?.takeIf(KtBinaryExpression::joinable)
            ?: return -1

        val lineCount = binaryExpr.getLineCount()

        var parent = binaryExpr.parent
        while (parent is KtBinaryExpression && parent.joinable() && parent.getLineCount() == lineCount) {
            binaryExpr = parent
            parent = parent.parent
        }

        var rightText = unescape(binaryExpr.right as KtStringTemplateExpression)
        var left = binaryExpr.left
        while (left is KtBinaryExpression && left.joinable()) {
            val leftLeft = (left as? KtBinaryExpression)?.left ?: break
            if (leftLeft.getLineCount() < lineCount - 1) break
            rightText = unescape(left.right as KtStringTemplateExpression) + rightText
            left = left.left
        }

        return when (left) {
            is KtStringTemplateExpression -> {
                val offset = left.endOffset - 1
                binaryExpr.replace(createStringTemplate(left, rightText))
                offset
            }
            is KtBinaryExpression -> {
                val leftRight = left.right
                if (leftRight is KtStringTemplateExpression) {
                    val offset = leftRight.endOffset - 1
                    leftRight.replace(createStringTemplate(leftRight, rightText))
                    binaryExpr.replace(left)
                    offset
                } else {
                    -1
                }
            }
            else -> -1
        }
    }

    private fun unescape(expr: KtStringTemplateExpression): String {
        val expressionText = expr.text
        return if (expressionText.startsWith("\"\"\"") && expressionText.endsWith("\"\"\"")) {
            val unquoted = expressionText.substring(3, expressionText.length - 3)
            StringUtil.escapeStringCharacters(unquoted)
        } else {
            StringUtil.unquoteString(expressionText)
        }
    }

    private fun createStringTemplate(left: KtStringTemplateExpression, rightText: String): KtStringTemplateExpression {
        val leftText = unescape(left)
        return KtPsiFactory(left.project).createExpression("\"$leftText$rightText\"") as KtStringTemplateExpression
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = -1
}

private fun KtBinaryExpression.joinable(): Boolean {
    if (operationToken != KtTokens.PLUS) return false
    if (right !is KtStringTemplateExpression) return false
    return when (val left = left) {
        is KtStringTemplateExpression -> true
        is KtBinaryExpression -> left.right is KtStringTemplateExpression
        else -> false
    }
}
