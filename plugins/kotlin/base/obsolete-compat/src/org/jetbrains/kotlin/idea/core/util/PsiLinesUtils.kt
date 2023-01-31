// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("DeprecatedCallableAddReplaceWith")

package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.math.abs

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineStartOffset() instead.")
fun PsiFile.getLineStartOffset(line: Int): Int? {
    return getLineStartOffset(line, skipWhitespace = true)
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineStartOffset() instead.")
fun PsiFile.getLineStartOffset(line: Int, skipWhitespace: Boolean): Int? {
    val doc = viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(this)
    if (doc != null && line >= 0 && line < doc.lineCount) {
        val startOffset = doc.getLineStartOffset(line)
        val element = findElementAt(startOffset) ?: return startOffset

        if (skipWhitespace && (element is PsiWhiteSpace || element is PsiComment)) {
            return PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace::class.java, PsiComment::class.java)?.startOffset ?: startOffset
        }
        return startOffset
    }

    return null
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineEndOffset() instead.")
fun PsiFile.getLineEndOffset(line: Int): Int? {
    val document = viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(this)
    return document?.getLineEndOffset(line)
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineNumber() instead.")
fun PsiElement.getLineNumber(start: Boolean = true): Int {
    val document = containingFile.viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(containingFile)
    return document?.getLineNumber(if (start) this.startOffset else this.endOffset) ?: 0
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineCount() instead.")
fun PsiElement.getLineCount(): Int {
    val doc = containingFile?.let { file -> PsiDocumentManager.getInstance(project).getDocument(file) }
    if (doc != null) {
        val spaceRange = textRange ?: TextRange.EMPTY_RANGE

        if (spaceRange.endOffset <= doc.textLength && spaceRange.startOffset < spaceRange.endOffset) {
            val startLine = doc.getLineNumber(spaceRange.startOffset)
            val endLine = doc.getLineNumber(spaceRange.endOffset)

            return endLine - startLine + 1
        }
    }

    return StringUtil.getLineBreakCount(text ?: error("Cannot count number of lines")) + 1
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.isMultiLine() instead.")
fun PsiElement.isMultiLine() = getLineCount() > 1

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.isOneLiner() instead.")
fun PsiElement.isOneLiner() = getLineCount() == 1

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineCountInRange() instead.")
fun Document.getLineCountInRange(textRange: TextRange): Int = abs(getLineNumber(textRange.startOffset) - getLineNumber(textRange.endOffset))

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.containsLineBreakInRange() instead.")
fun Document.containsLineBreakInRange(textRange: TextRange): Boolean = getLineCountInRange(textRange) != 0
