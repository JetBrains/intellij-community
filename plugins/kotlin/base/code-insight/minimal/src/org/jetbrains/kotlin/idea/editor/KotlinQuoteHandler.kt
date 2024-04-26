// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.jetbrains.kotlin.lexer.KtTokens

class KotlinQuoteHandler : QuoteHandler {
    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val tokenType = iterator.tokenType

        if (tokenType == KtTokens.CHARACTER_LITERAL) {
            val start = iterator.start
            val end = iterator.end
            return end - start >= 1 && offset == end - 1
        } else if (tokenType == KtTokens.CLOSING_QUOTE) {
            return true
        }
        return false
    }

    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val tokenType = iterator.tokenType

        if (tokenType == KtTokens.OPEN_QUOTE || tokenType == KtTokens.CHARACTER_LITERAL) {
            val start = iterator.start
            return offset == start
        }
        return false
    }

    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        return true
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean {
        val tokenType = iterator.tokenType
        return tokenType == KtTokens.REGULAR_STRING_PART ||
                tokenType == KtTokens.OPEN_QUOTE ||
                tokenType == KtTokens.CLOSING_QUOTE ||
                tokenType == KtTokens.SHORT_TEMPLATE_ENTRY_START ||
                tokenType == KtTokens.LONG_TEMPLATE_ENTRY_END ||
                tokenType == KtTokens.LONG_TEMPLATE_ENTRY_START
    }
}
