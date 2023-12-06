// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.inspections.FoldInitializerAndIfToElvisInspection
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.siblings

class JoinInitializerAndIfToElvisHandler : JoinRawLinesHandlerDelegate {

    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return -1
        val lineBreak = file.findElementAt(start)
            ?.siblings(forward = true, withItself = true)
            ?.firstOrNull { it.textContains('\n') }
            ?: return -1
        val ifExpression = lineBreak.getNextSiblingIgnoringWhitespaceAndComments() as? KtIfExpression ?: return -1
        if (!FoldInitializerAndIfToElvisInspection.Util.isApplicable(ifExpression)) return -1
        return FoldInitializerAndIfToElvisInspection.Util.applyTo(ifExpression).textRange.startOffset
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int) = -1
}