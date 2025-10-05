// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinExpressionParsing
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class JoinStatementsAddSemicolonHandler : JoinRawLinesHandlerDelegate {

    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file.fileType !is KotlinFileType) return JoinLinesHandlerDelegate.CANNOT_JOIN

        val startElement = file.findElementAt(start)
        val endElement = file.findElementAt(end)
        val endElementTextRange = endElement?.textRange

        val linebreak = startElement
            ?.siblings(forward = true, withItself = true)
            ?.takeWhile { endElementTextRange != null && it.startOffset <= endElementTextRange.startOffset }
            ?.firstOrNull { it.textContains('\n') }
            ?: return JoinLinesHandlerDelegate.CANNOT_JOIN

        val parent = linebreak.parent
            ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
        val element1 = linebreak.firstMaterialSiblingSameLine { prevSibling }
            ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
        val element2 = linebreak.firstMaterialSiblingSameLine { nextSibling }
            ?: return JoinLinesHandlerDelegate.CANNOT_JOIN

        if (element1 !is KtPropertyAccessor) {
            val parentOfElement1 = element1.parent
            if (parentOfElement1 is KtProperty && parentOfElement1.initializer == null && element2 is KtPropertyAccessor) {
                return JoinLinesHandlerDelegate.CANNOT_JOIN
            }
        }

        if (
          (element1 !is KtPropertyAccessor && element2 !is KtPropertyAccessor) &&
          // `val x=1 fun f(){}` is error; while `fun f(){} val x=1` is OK
          (element1 !is KtProperty)
        ) {
            if (parent.node.elementType != KtNodeTypes.BLOCK) return JoinLinesHandlerDelegate.CANNOT_JOIN
            if (!element1.isStatement()) return JoinLinesHandlerDelegate.CANNOT_JOIN
            if (!element2.isStatement()) return JoinLinesHandlerDelegate.CANNOT_JOIN
        }

        document.replaceString(linebreak.textRange.startOffset, linebreak.textRange.endOffset, " ")
        document.insertString(element1.textRange.endOffset, ";")

        return linebreak.textRange.startOffset + 1
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = JoinLinesHandlerDelegate.CANNOT_JOIN

    private fun PsiElement.firstMaterialSiblingSameLine(getNext: PsiElement.() -> PsiElement?): PsiElement? {
        var element = this
        do {
            element = element.getNext() ?: return null
            if (element.node.elementType !in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET)
                return element
        } while (!element.textContains('\n'))

        return null
    }

    private fun PsiElement.isStatement(): Boolean {
        if (this.node.elementType == KtTokens.LBRACE) return false

        var firstSubElement: PsiElement = this
        while (true) firstSubElement = firstSubElement.firstChild ?: break

        // Emulates the `atSet(STATEMENT_FIRST)` check at [KotlinExpressionParsing.parseStatements]
        return firstSubElement.node.elementType in KotlinExpressionParsing.STATEMENT_FIRST
    }
}