// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeaf
import org.jetbrains.kotlin.idea.base.psi.containsLineBreakInRange
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaHelper
import org.jetbrains.kotlin.idea.formatter.trailingComma.TrailingCommaState
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class JoinWithTrailingCommaHandler : JoinLinesHandlerDelegate {
    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN
        val startElement = file.findElementAt(start)
        val commaOwner = startElement
            ?.parentsWithSelf
            ?.filter { !document.containsLineBreakInRange(it.textRange) }
            ?.find { TrailingCommaState.stateForElement(it) == TrailingCommaState.REDUNDANT } as? KtElement
            ?: return CANNOT_JOIN

        val comma = TrailingCommaHelper.trailingCommaOrLastElement(commaOwner)?.takeIf {
            it.elementType == KtTokens.COMMA
        } ?: return CANNOT_JOIN

        comma.nextWhiteSpaceOrNull()?.delete()
        deleteOrReplaceWithWhiteSpace(comma, comma.nextLeaf()?.elementType)
        startElement.nextWhiteSpaceOrNull()?.let { deleteOrReplaceWithWhiteSpace(it, startElement.elementType) }

        return TrailingCommaHelper.elementAfterLastElement(commaOwner)?.startOffset ?: (end - 1)
    }

    companion object {
        private val TOKENS_WITH_SPACES = TokenSet.create(
            KtTokens.ARROW,
            KtTokens.RBRACE,
            KtTokens.LBRACE,
            KtTokens.COMMA,
        )

        private fun deleteOrReplaceWithWhiteSpace(element: PsiElement, type: IElementType?) {
            if (type !in TOKENS_WITH_SPACES) {
                element.delete()
            } else {
                element.replace(KtPsiFactory(element.project).createWhiteSpace())
            }
        }
    }
}

private fun PsiElement.nextWhiteSpaceOrNull(): PsiElement? = nextLeaf().takeIf { it is PsiWhiteSpace }
