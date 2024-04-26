// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.isConvertableToExpressionBody
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceWithExpressionBodyPreservingComments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MergeIfsUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class JoinBlockIntoSingleStatementHandler : JoinLinesHandlerDelegate {
    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN

        if (start == 0) return CANNOT_JOIN
        val c = document.charsSequence[start]
        val index = if (c == '\n') start - 1 else start

        val brace = file.findElementAt(index) ?: return CANNOT_JOIN
        if (brace.elementType != KtTokens.LBRACE) return CANNOT_JOIN

        val block = brace.parent as? KtBlockExpression ?: return CANNOT_JOIN
        val statement = block.statements.singleOrNull() ?: return CANNOT_JOIN

        val parent = block.parent
        val oneLineReturnFunction = (parent as? KtDeclarationWithBody)?.takeIf { it.isConvertableToExpressionBody() }
        if (parent !is KtContainerNode && parent !is KtWhenEntry && oneLineReturnFunction == null) return CANNOT_JOIN

        if (block.node.getChildren(KtTokens.COMMENTS).isNotEmpty()) return CANNOT_JOIN // otherwise we will loose comments

        // handle nested if's
        val pparent = parent.parent
        if (pparent is KtIfExpression) {
            if (block == pparent.then && statement is KtIfExpression && statement.`else` == null) {
                // if outer if has else-branch and inner does not have it, do not remove braces otherwise else-branch will belong to different if!
                if (pparent.`else` != null) return CANNOT_JOIN

                return MergeIfsUtils.mergeNestedIf(pparent)
            }

            if (block == pparent.`else`) {
                val ifParent = pparent.parent
                if (!(ifParent is KtBlockExpression || ifParent is KtDeclaration || KtPsiUtil.isAssignment(ifParent))) {
                    return CANNOT_JOIN
                }
            }
        }

        val resultExpression = if (oneLineReturnFunction != null) {
            oneLineReturnFunction.replaceWithExpressionBodyPreservingComments()
            oneLineReturnFunction.bodyExpression ?: return CANNOT_JOIN
        } else {
            block.replace(statement)
        }

        return resultExpression.reformatted(true).startOffset
    }
}
