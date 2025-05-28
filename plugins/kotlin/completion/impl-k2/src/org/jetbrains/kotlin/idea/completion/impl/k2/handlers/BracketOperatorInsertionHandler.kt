// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

internal object BracketOperatorInsertionHandler : InsertHandler<LookupElement> {
    private fun CharSequence.skipWhitespaceBackwards(startIndex: Int): Int? = startIndex.downTo(0).firstOrNull {
        !this[it].isWhitespace()
    }

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val documentSequence = context.document.charsSequence
        val bracketRange = context.document.createRangeMarker(context.startOffset, context.tailOffset)
        // Scan backwards before the brackets to find the dot that was inserted before
        val indexOfCharBefore = documentSequence.skipWhitespaceBackwards(context.startOffset - 1) ?: -1
        val charBefore = context.document.charsSequence[indexOfCharBefore]
        if (charBefore != '.') {
            return
        }
        // Also include any whitespace directly preceding the dot
        val indexBeforeDot = documentSequence.skipWhitespaceBackwards(indexOfCharBefore - 1) ?: return
        // Delete the dot and any preceding whitespace because the bracket should always follow without whitespace
        context.document.deleteString(indexBeforeDot + 1, context.startOffset)
        context.commitDocument()

        // The bracket PSI element
        val element = context.file.findElementAt(bracketRange.startOffset + 1) ?: return

        // Move caret into the brackets
        context.editor.caretModel.moveToOffset(bracketRange.startOffset + 1)
        AutoPopupController.getInstance(context.project).autoPopupParameterInfo(context.editor, element)
    }
}