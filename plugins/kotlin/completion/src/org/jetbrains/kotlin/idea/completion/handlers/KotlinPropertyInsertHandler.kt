// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.util.CallType

class KotlinPropertyInsertHandler(callType: CallType<*>) : KotlinCallableInsertHandler(callType) {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val surroundedWithBraces = surroundWithBracesIfInStringTemplate(context)

        super.handleInsert(context, item)

        if (context.completionChar == Lookup.REPLACE_SELECT_CHAR) {
            deleteEmptyParenthesis(context)
        }

        if (surroundedWithBraces) {
            removeRedundantBracesInStringTemplate(context)
        }
    }

    private fun deleteEmptyParenthesis(context: InsertionContext) {
        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.commitDocument(context.document)
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

        val offset = context.tailOffset
        val document = context.document
        val chars = document.charsSequence

        val lParenOffset = chars.indexOfSkippingSpace('(', offset) ?: return
        val rParenOffset = chars.indexOfSkippingSpace(')', lParenOffset + 1) ?: return

        document.deleteString(offset, rParenOffset + 1)
    }
}