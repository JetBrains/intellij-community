// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

open class KotlinCompletionCharFilter : CharFilter() {
    companion object {
        val JUST_TYPING_PREFIX: Key<String> = Key("KotlinCompletionCharFilter.JUST_TYPING_PREFIX")
    }

    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        if (lookup.psiFile !is KtFile) return null
        if (!lookup.isCompletion) return null
        val isAutopopup = CompletionService.getCompletionService().currentCompletion?.isAutopopupCompletion ?: return null

        if (Character.isJavaIdentifierPart(c) || c == '@') {
            return Result.ADD_TO_PREFIX
        }

        val currentItem = lookup.currentItem

        // do not accept items by special chars in some special positions such as in the very beginning of function literal where name of the first parameter can be
        if (isAutopopup && !lookup.isSelectionTouched && currentItem?.suppressItemSelectionByCharsOnTyping == true) {
            return Result.HIDE_LOOKUP
        }

        if (c == ':') {
            return when {
                currentItem?.hideLookupOnColon == true -> Result.HIDE_LOOKUP
                else -> Result.ADD_TO_PREFIX /* used in '::xxx'*/
            }
        }

        if (!lookup.isSelectionTouched) {
            currentItem?.putUserDataDeep(JUST_TYPING_PREFIX, lookup.itemPattern(currentItem))
        }

        val customResult = customResult(c, prefixLength, lookup)
        if (customResult != null) {
            return customResult
        }
        return when (c) {
            '.' -> {
                if (prefixLength == 0 && isAutopopup && !lookup.isSelectionTouched) {
                    val caret = lookup.editor.caretModel.offset
                    if (caret > 0 && lookup.editor.document.charsSequence[caret - 1] == '.') {
                        return Result.HIDE_LOOKUP
                    }
                }
                if (isWithinStringLiteral(lookup)) Result.ADD_TO_PREFIX else Result.SELECT_ITEM_AND_FINISH_LOOKUP
            }

            '{' -> {
                if (currentItem?.acceptOpeningBrace == true)
                    Result.SELECT_ITEM_AND_FINISH_LOOKUP
                else
                    Result.HIDE_LOOKUP
            }

            ',', ' ', '(', '=', '!' -> Result.SELECT_ITEM_AND_FINISH_LOOKUP

            else -> Result.HIDE_LOOKUP
        }
    }

    open fun customResult(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        return null
    }

    private fun isWithinStringLiteral(lookup: Lookup): Boolean = lookup.psiElement?.parent is KtStringTemplateExpression
}

var LookupElement.suppressItemSelectionByCharsOnTyping: Boolean by NotNullableUserDataProperty(
    Key("KOTLIN_SUPPRESS_ITEM_SELECTION_BY_CHARS_ON_TYPING"),
    defaultValue = false,
)

var LookupElement.acceptOpeningBrace: Boolean by NotNullableUserDataProperty(
    Key("KOTLIN_ACCEPT_OPENING_BRACE"),
    defaultValue = false,
)

var LookupElement.hideLookupOnColon: Boolean by NotNullableUserDataProperty(
    Key("KOTLIN_HIDE_LOOKUP_ON_COLON"),
    defaultValue = false,
)