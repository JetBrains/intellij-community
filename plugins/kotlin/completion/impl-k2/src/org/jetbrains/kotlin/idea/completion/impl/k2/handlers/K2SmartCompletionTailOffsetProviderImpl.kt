// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.OffsetKey
import com.intellij.codeInsight.completion.OffsetMap
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.handlers.SmartCompletionTailOffsetProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.K2SmartCompletionTailOffsetProviderImpl.Companion.OLD_ARGUMENTS_REPLACEMENT_OFFSET
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import kotlin.math.max

/**
 * This class and the remaining file is responsible for handling the replacement offsets in smart completion
 * when a user decides to use the `\t` replacement character.
 *
 * The code is ported entirely from the corresponding K1 implementation:
 * [org.jetbrains.kotlin.idea.completion.handlers.SmartCompletionTailOffsetProviderFE10Impl]
 */
internal class K2SmartCompletionTailOffsetProviderImpl : SmartCompletionTailOffsetProvider() {

    companion object {
        internal val OLD_ARGUMENTS_REPLACEMENT_OFFSET: OffsetKey = OffsetKey.create("nonFunctionReplacementOffset")
        internal val MULTIPLE_ARGUMENTS_REPLACEMENT_OFFSET: OffsetKey = OffsetKey.create("multipleArgumentsReplacementOffset")

        internal fun calculateReplacementOffsets(context: CompletionInitializationContext) {
            val offset = context.startOffset
            val tokenAt = context.file.findElementAt(max(0, offset)) ?: return
            /* do not use parent expression if we are at the end of line - it's probably parsed incorrectly */
            if (context.completionType == CompletionType.SMART && !isAtEndOfLine(offset, context.editor.document)) {
                var parent = tokenAt.parent
                if (parent is KtExpression && parent !is KtBlockExpression) {
                    // search expression to be replaced - go up while we are the first child of parent expression
                    var expression: KtExpression = parent
                    parent = expression.parent
                    while (parent is KtExpression && parent.getFirstChild() == expression) {
                        expression = parent
                        parent = expression.parent
                    }

                    val suggestedReplacementOffset = replacementOffsetByExpression(expression)
                    if (suggestedReplacementOffset > context.replacementOffset) {
                        context.replacementOffset = suggestedReplacementOffset
                    }

                    context.offsetMap.addOffset(OLD_ARGUMENTS_REPLACEMENT_OFFSET, expression.endOffset)

                    val argumentList = (expression.parent as? KtValueArgument)?.parent as? KtValueArgumentList
                    if (argumentList != null) {
                        context.offsetMap.addOffset(
                            MULTIPLE_ARGUMENTS_REPLACEMENT_OFFSET,
                            argumentList.rightParenthesis?.textRange?.startOffset ?: argumentList.endOffset
                        )
                    }
                }
            }
            CompletionDummyIdentifierProviderService.getInstance().correctPositionForParameter(context)
        }
    }

    override fun getTailOffset(
        context: InsertionContext,
        item: LookupElement
    ): Int {
        val completionChar = context.completionChar
        var tailOffset = context.tailOffset
        if (completionChar == Lookup.REPLACE_SELECT_CHAR && item.getUserData(KEEP_OLD_ARGUMENT_LIST_ON_TAB_KEY) != null) {
            context.offsetMap.tryGetOffset(OLD_ARGUMENTS_REPLACEMENT_OFFSET)
                ?.let { tailOffset = it }
        }
        return tailOffset
    }
}

internal val KEEP_OLD_ARGUMENT_LIST_ON_TAB_KEY = Key<Unit>("KEEP_OLD_ARGUMENT_LIST_ON_TAB_KEY")

internal fun LookupElement.keepOldArgumentListOnTab(): LookupElement {
    putUserData(KEEP_OLD_ARGUMENT_LIST_ON_TAB_KEY, Unit)
    return this
}

/**
 * This insertion handler is responsible for replacing the entire following argument if
 * replacement completion is used (e.g., using the `\t` key).
 * It uses the [OLD_ARGUMENTS_REPLACEMENT_OFFSET] to know the end offset of the argument we are currently replacing.
 */
@Serializable
internal class SmartCompletionReplaceExistingArgumentHandler : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        if (context.completionChar == Lookup.REPLACE_SELECT_CHAR) {
            val offset = context.offsetMap.tryGetOffset(OLD_ARGUMENTS_REPLACEMENT_OFFSET)
            if (offset != null) {
                context.document.deleteString(context.tailOffset, offset)
            }
        }
        item.handleInsert(context)
    }

}

internal fun OffsetMap.tryGetOffset(key: OffsetKey): Int? {
    try {
        if (!containsOffset(key)) return null
        return getOffset(key).takeIf { it != -1 } // prior to IDEA 2016.3 getOffset() returned -1 if not found, now it throws exception
    } catch (_: Exception) {
        return null
    }
}

private fun isAtEndOfLine(offset: Int, document: Document): Boolean {
    var i = offset
    val chars = document.charsSequence
    while (i < chars.length) {
        val c = chars[i]
        if (c == '\n') return true
        if (!Character.isWhitespace(c)) return false
        i++
    }
    return true
}

private fun replacementOffsetByExpression(expression: KtExpression): Int {
    when (expression) {
        is KtCallExpression -> {
            val calleeExpression = expression.calleeExpression
            if (calleeExpression != null) {
                return calleeExpression.textRange!!.endOffset
            }
        }

        is KtQualifiedExpression -> {
            val selector = expression.selectorExpression
            if (selector != null) {
                return replacementOffsetByExpression(selector)
            }
        }
    }
    return expression.textRange!!.endOffset
}