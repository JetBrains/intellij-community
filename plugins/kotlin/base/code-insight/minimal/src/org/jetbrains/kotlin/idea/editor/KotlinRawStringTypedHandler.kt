// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens

class KotlinRawStringTypedHandler : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (c != '"') {
            return Result.CONTINUE
        }
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
            return Result.CONTINUE
        }
        if (file.fileType != KotlinFileType.INSTANCE) {
            return Result.CONTINUE
        }

        // A quote is typed after 2 other quotes
        val offset = editor.caretModel.offset
        if (offset < 2) {
            return Result.CONTINUE
        }

        val openQuote = file.findElementAt(offset - 2)
        if (openQuote == null || openQuote !is LeafPsiElement || openQuote.elementType != KtTokens.OPEN_QUOTE) {
            return Result.CONTINUE
        }

        val closeQuote = file.findElementAt(offset - 1)
        if (closeQuote == null || closeQuote !is LeafPsiElement || closeQuote.elementType != KtTokens.CLOSING_QUOTE) {
            return Result.CONTINUE
        }

        if (closeQuote.text != "\"") {
            // Check it is not a multi-line quote
            return Result.CONTINUE
        }

        editor.document.insertString(offset, "\"\"\"\"")
        editor.caretModel.currentCaret.moveToOffset(offset + 1)

        return Result.STOP
    }
}