// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class JoinDeclarationAndAssignmentHandler : JoinRawLinesHandlerDelegate {
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file.fileType !is KotlinFileType) return JoinLinesHandlerDelegate.CANNOT_JOIN

        val element = file.findElementAt(start)
            ?.siblings(forward = false, withItself = false)
            ?.firstOrNull { !isToSkip(it) } ?: return JoinLinesHandlerDelegate.CANNOT_JOIN

        val (property, assignment) = element.parentsWithSelf.mapNotNull { getPropertyAndAssignment(it) }.firstOrNull() ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
        document.replaceString(property.endOffset, assignment.operationReference.startOffset, " ")
        return property.startOffset
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int) = JoinLinesHandlerDelegate.CANNOT_JOIN

    private fun getPropertyAndAssignment(element: PsiElement): Pair<KtProperty, KtBinaryExpression>? {
        val property = element as? KtProperty ?: return null
        if (property.hasInitializer()) return null

        val assignment = element.siblings(forward = true, withItself = false)
            .firstOrNull { !isToSkip(it) } as? KtBinaryExpression ?: return null

        if (assignment.operationToken != KtTokens.EQ) return null

        val left = assignment.left as? KtSimpleNameExpression ?: return null
        if (assignment.right == null) return null
        if (left.getReferencedName() != property.name) return null

        return property to assignment
    }

    private fun isToSkip(element: PsiElement): Boolean = when (element) {
        is PsiWhiteSpace -> StringUtil.getLineBreakCount(element.text) <= 1 // do not skip blank line
        else -> element.elementType == KtTokens.SEMICOLON
    }
}