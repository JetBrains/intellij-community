// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Document
import com.intellij.psi.util.elementType
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression

@Serializable
internal object WrapSingleStringTemplateEntryWithBracesInsertHandler : SerializableInsertHandler {

    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement,
    ) {
        val document = context.document
        context.commitDocument()

        if (needInsertBraces(context)) {
            insertBraces(context, document)
            context.commitDocument()

            item.handleInsert(context)

            removeUnneededBraces(context)
            context.commitDocument()
        } else {
            item.handleInsert(context)
        }
    }

    private fun insertBraces(context: InsertionContext, document: Document) {
        val startOffset = context.startOffset
        document.insertString(context.startOffset, "{")
        context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset + 1)

        val tailOffset = context.tailOffset
        document.insertString(tailOffset, "}")
        context.tailOffset = tailOffset
    }

    private fun removeUnneededBraces(context: InsertionContext) {
        val templateEntry = getContainingTemplateEntry(context) as? KtBlockStringTemplateEntry ?: return
        templateEntry.dropCurlyBracketsIfPossible()
    }

    private fun needInsertBraces(context: InsertionContext): Boolean =
        getContainingTemplateEntry(context) is KtSimpleNameStringTemplateEntry

    private fun getContainingTemplateEntry(context: InsertionContext): KtStringTemplateEntryWithExpression? {
        val file = context.file
        val element = file.findElementAt(context.startOffset) ?: return null
        if (element.elementType != KtTokens.IDENTIFIER) return null
        val identifier = element.parent as? KtNameReferenceExpression ?: return null
        return identifier.parent as? KtStringTemplateEntryWithExpression
    }
}