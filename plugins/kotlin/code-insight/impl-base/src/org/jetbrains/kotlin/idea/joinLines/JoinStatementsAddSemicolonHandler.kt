// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.KtNodeTypes.BLOCK
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
import org.jetbrains.kotlin.parsing.KotlinExpressionParsing
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.siblings

class JoinStatementsAddSemicolonHandler : JoinRawLinesHandlerDelegate {

    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN

        val linebreak = file.findElementAt(start)
            ?.siblings(forward = true, withItself = true)
            ?.firstOrNull { it.textContains('\n') }
            ?: return CANNOT_JOIN

        val parent = linebreak.parent ?: return CANNOT_JOIN
        val element1 = linebreak.firstMaterialSiblingSameLine { prevSibling } ?: return CANNOT_JOIN
        val element2 = linebreak.firstMaterialSiblingSameLine { nextSibling } ?: return CANNOT_JOIN

        if (linebreak.text.count { it == '\n' } > 1) return CANNOT_JOIN

        if (element1 !is KtPropertyAccessor) {
            val parentOfElement1 = element1.parent
            if (parentOfElement1 is KtProperty && parentOfElement1.initializer == null && element2 is KtPropertyAccessor) {
                return CANNOT_JOIN
            }
        }

        if (
            (element1 !is KtPropertyAccessor && element2 !is KtPropertyAccessor) &&
            // `val x=1 fun f(){}` is error; while `fun f(){} val x=1` is OK
            (element1 !is KtProperty)
        ) {
            if (parent.node.elementType != BLOCK) return CANNOT_JOIN
            if (!element1.isStatement()) return CANNOT_JOIN
            if (!element2.isStatement()) return CANNOT_JOIN
        }

        document.replaceString(linebreak.textRange.startOffset, linebreak.textRange.endOffset, " ")
        document.insertString(element1.textRange.endOffset, ";")

        return linebreak.textRange.startOffset + 1
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = CANNOT_JOIN

    private fun PsiElement.firstMaterialSiblingSameLine(getNext: PsiElement.() -> PsiElement?): PsiElement? {
        var element = this
        do {
            element = element.getNext() ?: return null
            if (element.node.elementType !in WHITE_SPACE_OR_COMMENT_BIT_SET)
                return element
        } while (!element.textContains('\n'))

        return null
    }

    private fun PsiElement.isStatement(): Boolean {
        if (this.node.elementType == LBRACE) return false

        var firstSubElement: PsiElement = this
        while (true) firstSubElement = firstSubElement.firstChild ?: break

        // Emulates the `atSet(STATEMENT_FIRST)` check at [KotlinExpressionParsing.parseStatements]
        return firstSubElement.node.elementType in KotlinExpressionParsing.STATEMENT_FIRST
    }
}