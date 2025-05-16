// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.completion.InsertionHandlerBase
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.psi.KtFile

internal object GetOperatorInsertionHandler : InsertionHandlerBase<FunctionCallLookupObject>(FunctionCallLookupObject::class) {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement,
        ktFile: KtFile,
        lookupObject: FunctionCallLookupObject
    ) {
        val charAtStart = context.document.charsSequence[context.startOffset - 1]
        if (charAtStart != '.') {
            return
        }
        // Delete the dot
        context.document.deleteString(context.startOffset - 1, context.startOffset)
        context.commitDocument()
        val element = context.file.findElementAt(context.startOffset + 1) ?: return
        // Move caret into the brackets
        context.editor.caretModel.moveToOffset(context.startOffset + 1)
        AutoPopupController.getInstance(context.project).autoPopupParameterInfo(context.editor, element)
    }
}