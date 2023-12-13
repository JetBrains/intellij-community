// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import kotlin.let

class KotlinRawStringBackspaceHandler : BackspaceHandlerDelegate() {
    private var rangeMarker: RangeMarker? = null

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        rangeMarker = null
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
            return
        }
        if (file.fileType != KotlinFileType.INSTANCE) {
            return
        }
        val offset = editor.caretModel.offset
        val psiElement = file.findElementAt(offset) ?: return

        psiElement.parent?.let {
            if (it is KtStringTemplateExpression && it.text == "\"\"\"\"\"\"") {
                if (editor.caretModel.offset == it.textOffset + 3) {
                    rangeMarker = editor.document.createRangeMarker(it.textRange)
                }
            }
        }
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        rangeMarker?.let {
            editor.document.deleteString(it.startOffset, it.endOffset)
            rangeMarker = null
            return true
        }

        return false
    }
}