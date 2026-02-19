// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.KotlinCompletionCharFilter
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.getUserDataDeep
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler

@ApiStatus.Internal
@Serializable
data class CompletionCharInsertHandler(
    private val isAutoPopup: Boolean,
) : SerializableInsertHandler {

    // used to avoid insertion of spaces before/after ',', '=' on just typing
    private fun isJustTyping(
        context: InsertionContext,
        element: LookupElement,
    ): Boolean {
        if (!isAutoPopup) return false
        val insertedText = context.document.getText(TextRange(context.startOffset, context.tailOffset))
        return insertedText == element.getUserDataDeep(KotlinCompletionCharFilter.JUST_TYPING_PREFIX)
    }

    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement,
    ) {
        item.handleInsert(context)

        if (context.shouldAddCompletionChar() && !isJustTyping(context, item)) {
            when (context.completionChar) {
                ',' -> WithTailInsertHandler.COMMA.postHandleInsert(context, item)

                '=' -> WithTailInsertHandler.EQ.postHandleInsert(context, item)

                '!' -> {
                    WithExpressionPrefixInsertHandler("!").postHandleInsert(context)
                    context.setAddCompletionChar(false)
                }
            }
        }
    }
}
