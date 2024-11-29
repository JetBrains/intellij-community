// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.editor.Document
import com.intellij.patterns.ElementPattern
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass
import org.jetbrains.kotlin.idea.base.codeInsight.duration
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributor
import org.jetbrains.kotlin.idea.completion.implCommon.handlers.CompletionCharInsertHandler
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.InsertStringTemplateBracesInsertHandler
import org.jetbrains.kotlin.idea.completion.isAtFunctionLiteralStart
import org.jetbrains.kotlin.idea.completion.suppressItemSelectionByCharsOnTyping
import org.jetbrains.kotlin.idea.completion.weighers.CompletionContributorGroupWeigher.groupPriority
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import kotlin.time.Duration

internal class LookupElementSink(
    private val resultSet: CompletionResultSet,
    private val parameters: KotlinFirCompletionParameters,
    private val groupPriority: Int = 0,
    private val contributorClass: Class<FirCompletionContributor<*>>? = null,
) {

    var duration: Duration = Duration.ZERO
        private set

    val prefixMatcher: PrefixMatcher
        get() = resultSet.prefixMatcher

    fun withPriority(groupPriority: Int): LookupElementSink =
        LookupElementSink(resultSet, parameters, groupPriority, contributorClass)

    fun withContributorClass(contributorClass: Class<FirCompletionContributor<*>>): LookupElementSink =
        LookupElementSink(resultSet, parameters, groupPriority, contributorClass)

    fun addElement(element: LookupElement) {
        resultSet.addElement(decorateLookupElement(element))
    }

    fun addAllElements(elements: Iterable<LookupElement>) {
        resultSet.addAllElements(elements.map(::decorateLookupElement))
    }

    fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {
        resultSet.restartCompletionOnPrefixChange(prefixCondition)
    }

    private fun decorateLookupElement(
        element: LookupElement,
    ): LookupElementDecorator<LookupElement> {
        duration += element.duration

        element.groupPriority = groupPriority
        element.contributorClass = contributorClass

        if (isAtFunctionLiteralStart(parameters.position)) {
            element.suppressItemSelectionByCharsOnTyping = true
        }

        val bracesInsertHandler = when (parameters.type) {
            KotlinFirCompletionParameters.CorrectionType.BRACES_FOR_STRING_TEMPLATE -> InsertStringTemplateBracesInsertHandler
            else -> WrapSingleStringTemplateEntryWithBracesInsertHandler
        }

        return LookupElementDecorator.withDelegateInsertHandler(
            LookupElementDecorator.withDelegateInsertHandler(element, bracesInsertHandler),
            CompletionCharInsertHandler(parameters.delegate),
        )
    }
}

private object WrapSingleStringTemplateEntryWithBracesInsertHandler : InsertHandler<LookupElement> {

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
