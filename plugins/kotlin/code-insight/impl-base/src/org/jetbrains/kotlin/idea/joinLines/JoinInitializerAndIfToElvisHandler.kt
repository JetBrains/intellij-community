// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeInsight.joinLines
import org.jetbrains.kotlin.idea.codeInsight.prepareData
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.siblings
import kotlin.sequences.firstOrNull

class JoinInitializerAndIfToElvisHandler : JoinRawLinesHandlerDelegate {

    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN
        val lineBreak = file.findElementAt(start)
            ?.siblings(forward = true, withItself = true)
            ?.firstOrNull { it.textContains('\n') }
            ?: return -1
        val ifExpression = lineBreak.getNextSiblingIgnoringWhitespaceAndComments() as? KtIfExpression ?: return CANNOT_JOIN
        val data = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(ifExpression) {
                  prepareData(ifExpression) ?: return CANNOT_JOIN
                }
            }
        }
        return joinLines(ifExpression, data.variableDeclaration, data.initializer, data.ifNullExpression, data.typeChecked,
                         data.variableTypeString).textRange.startOffset
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int) = CANNOT_JOIN
}