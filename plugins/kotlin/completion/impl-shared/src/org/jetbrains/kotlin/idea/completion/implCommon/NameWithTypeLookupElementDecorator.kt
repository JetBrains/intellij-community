// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.completion.handlers.skipSpaces
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings

class NameWithTypeLookupElementDecorator(
    private val parameterName: String,
    private val typeIdString: String,
    typeLookupElement: LookupElement,
    private val shouldInsertType: Boolean
) : LookupElementDecorator<LookupElement>(typeLookupElement) {

    private val lookupString = parameterName + if (shouldInsertType) ": " + delegate.lookupString else ""

    override fun getLookupString() = lookupString
    override fun getAllLookupStrings() = setOf(lookupString)

    override fun renderElement(presentation: LookupElementPresentation) {
        super.renderElement(presentation)
        if (shouldInsertType) {
            presentation.itemText = parameterName + ": " + presentation.itemText
        } else {
            presentation.prependTailText(": " + presentation.itemText, true)
            presentation.itemText = parameterName
        }
    }

    override fun getDelegateInsertHandler(): InsertHandler<LookupElement> = InsertHandler { context, element ->
        if (context.completionChar == Lookup.REPLACE_SELECT_CHAR) {
            val tailOffset = context.tailOffset

            val chars = context.document.charsSequence
            var offset = chars.skipSpaces(tailOffset)
            if (chars.isCharAt(offset, ',')) {
                offset++
                offset = chars.skipSpaces(offset)
                context.editor.moveCaret(offset)
            }
        }
        val settings = context.file.kotlinCustomSettings
        val spaceBefore = if (settings.SPACE_BEFORE_TYPE_COLON) " " else ""
        val spaceAfter = if (settings.SPACE_AFTER_TYPE_COLON) " " else ""

        val startOffset = context.startOffset
        if (shouldInsertType) {
            val text = "$parameterName$spaceBefore:$spaceAfter"
            context.document.insertString(startOffset, text)

            // update start offset so that it does not include the text we inserted
            context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset + text.length)

            element.handleInsert(context)
        } else {
            context.document.replaceString(startOffset, context.tailOffset, parameterName)

            context.commitDocument()
        }
    }

    override fun equals(other: Any?) = other is NameWithTypeLookupElementDecorator &&
            parameterName == other.parameterName &&
            typeIdString == other.typeIdString &&
            shouldInsertType == other.shouldInsertType

    override fun hashCode() = parameterName.hashCode()
}

