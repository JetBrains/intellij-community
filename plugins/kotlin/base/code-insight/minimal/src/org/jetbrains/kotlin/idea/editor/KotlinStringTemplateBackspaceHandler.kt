// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens

class KotlinStringTemplateBackspaceHandler : BackspaceHandlerDelegate() {
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        if (c != '{' || file.fileType != KotlinFileType.INSTANCE || !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return

        val offset = editor.caretModel.offset

        val highlighter = (editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(offset)
        if (iterator.tokenType != KtTokens.LONG_TEMPLATE_ENTRY_END) return
        iterator.retreat()
        if (iterator.tokenType != KtTokens.LONG_TEMPLATE_ENTRY_START) return
        editor.document.deleteString(offset, offset + 1)
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        return false
    }
}