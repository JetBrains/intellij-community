// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.utils.checkWithAttachment

internal fun InsertionContext.insertStringAndInvokeCompletion(stringToInsert: String) {
    this.insertString(stringToInsert)
    scheduleCompletion(this)
}

private fun scheduleCompletion(context: InsertionContext) {
    ApplicationManager.getApplication().invokeLater {
        if (!context.editor.isDisposed) {
            CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true)
                .invokeCompletion(context.project, context.editor)
        }
    }
}

internal fun InsertionContext.insertString(stringToInsert: String, position: Int = tailOffset, moveCaretToEnd: Boolean = true) {
    val rangeMarker = document.createRangeMarker(position, position)
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

    checkWithAttachment(rangeMarker.isValid, { "Unexpected invalid range marker" }) {
        it.withAttachment("document", document)
        it.withAttachment("rangeMarker", rangeMarker)
    }
    val updatedPosition = rangeMarker.startOffset

    document.insertString(updatedPosition, stringToInsert)
    commitDocument()
    if (moveCaretToEnd) editor.caretModel.moveToOffset(tailOffset)
}

internal fun InsertionContext.addTypeArguments(typeArgumentsCount: Int) {
    when {
        typeArgumentsCount == 0 -> {
            return
        }

        typeArgumentsCount < 0 -> {
            error("Count of type arguments should be non-negative, but was $typeArgumentsCount")
        }

        else -> {
            commitDocument()
            insertString(createStarTypeArgumentsList(typeArgumentsCount))
        }
    }
}