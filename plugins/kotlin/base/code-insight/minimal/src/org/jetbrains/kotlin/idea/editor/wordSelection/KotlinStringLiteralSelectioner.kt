// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinStringLiteralSelectioner : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement) = e is KtStringTemplateExpression

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val entries = (e as KtStringTemplateExpression).entries
        if (entries.isEmpty()) return null
        val start = entries.first().startOffset
        val end = entries.last().endOffset
        return listOf(TextRange(start, end))
    }
}
