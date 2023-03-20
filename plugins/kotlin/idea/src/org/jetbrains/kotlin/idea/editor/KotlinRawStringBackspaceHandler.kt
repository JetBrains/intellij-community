// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinRawStringBackspaceHandler : BackspaceHandlerDelegate() {
    private var rangeMarker: RangeMarker? = null

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        rangeMarker = null
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
            return
        }
        if (file !is KtFile) {
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