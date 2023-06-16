// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.handlers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.KotlinCompletionCharFilter
import org.jetbrains.kotlin.idea.completion.getUserDataDeep
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler

@ApiStatus.Internal
class HandleCompletionCharLookupElementDecorator(element: LookupElement, private val completionParameters: CompletionParameters) :
    LookupElementDecorator<LookupElement>(element) {
    // used to avoid insertion of spaces before/after ',', '=' on just typing
    private fun isJustTyping(context: InsertionContext, element: LookupElement): Boolean {
        if (!completionParameters.isAutoPopup) return false
        val insertedText = context.document.getText(TextRange(context.startOffset, context.tailOffset))
        return insertedText == element.getUserDataDeep(KotlinCompletionCharFilter.JUST_TYPING_PREFIX)
    }

    override fun getDecoratorInsertHandler(): InsertHandler<LookupElementDecorator<LookupElement>> = InsertHandler { context, decorator ->
        delegate.handleInsert(context)

        if (context.shouldAddCompletionChar() && !isJustTyping(context, this)) {
            when (context.completionChar) {
                ',' -> WithTailInsertHandler.COMMA.postHandleInsert(context, delegate)

                '=' -> WithTailInsertHandler.EQ.postHandleInsert(context, delegate)

                '!' -> {
                    WithExpressionPrefixInsertHandler("!").postHandleInsert(context)
                    context.setAddCompletionChar(false)
                }
            }
        }
    }
}
