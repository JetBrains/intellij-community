// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.completion.handlers.skipSpaces
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.psi.*

object NameWithTypeCompletion {
    fun shouldCompleteParameter(parameter: KtParameter): Boolean {
        val list = parameter.parent as? KtParameterList ?: return false
        return when (val owner = list.parent) {
            is KtCatchClause, is KtPropertyAccessor, is KtFunctionLiteral -> false
            is KtNamedFunction -> owner.nameIdentifier != null
            is KtPrimaryConstructor -> !owner.getContainingClassOrObject().isAnnotation()
            else -> true
        }
    }

    /**
     * This pattern is used to check if completion needs to be restarted (which is true when an upper case letter is typed
     * and new completion suggestions may appear)
     */
    val prefixEndsWithUppercaseLetterPattern =
        StandardPatterns.string().with(object : PatternCondition<String>("Prefix ends with uppercase letter") {
            override fun accepts(prefix: String, context: ProcessingContext?) = prefix.isNotEmpty() && prefix.last().isUpperCase()
        })
}

/**
 * @param typeIdString a string which contains a qualified type or a qualified class name, depending on how the element was obtained;
 * it is used to compare lookup elements.
 */
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

    override fun getDelegateInsertHandler(): InsertHandler<LookupElement> =
        NameWithTypeLookupElementDecoratorInsertHandler(parameterName, typeIdString, shouldInsertType)

    override fun equals(other: Any?) = other is NameWithTypeLookupElementDecorator &&
            parameterName == other.parameterName &&
            typeIdString == other.typeIdString &&
            shouldInsertType == other.shouldInsertType

    override fun hashCode() = parameterName.hashCode()
}


@Serializable
data class NameWithTypeLookupElementDecoratorInsertHandler(
    private val parameterName: String,
    private val typeIdString: String,
    private val shouldInsertType: Boolean
) : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
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
            context.commitDocument()

            // update start offset so that it does not include the text we inserted
            context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset + text.length)

            item.handleInsert(context)
        } else {
            context.document.replaceString(startOffset, context.tailOffset, parameterName)

            context.commitDocument()
        }
    }
}