// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion.impl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItemInsertHandler
import com.intellij.webSymbols.impl.scaleToHeight
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal data class WebSymbolCodeCompletionItemImpl(override val name: String,
                                                    override val offset: Int = 0,
                                                    override val completeAfterInsert: Boolean = false,
                                                    override val completeAfterChars: Set<Char> = emptySet(),
                                                    override val displayName: String? = null,
                                                    override val symbol: WebSymbol? = null,
                                                    override val priority: WebSymbol.Priority? = null,
                                                    override val proximity: Int? = null,
                                                    override val deprecated: Boolean = false,
                                                    override val aliases: Set<String> = emptySet(),
                                                    override val icon: Icon? = null,
                                                    override val typeText: String? = null,
                                                    override val tailText: String? = null,
                                                    override val insertHandler: WebSymbolCodeCompletionItemInsertHandler? = null,
                                                    @get:ApiStatus.Internal
                                                    val stopSequencePatternEvaluation: Boolean = false) : WebSymbolCodeCompletionItem {

  override fun addToResult(parameters: CompletionParameters,
                           result: CompletionResultSet,
                           baselinePriorityValue: Double) {
    val completionPrefixMatcher = result.prefixMatcher
    val completionPrefix = completionPrefixMatcher.prefix
    if (completionPrefix.length < offset ||
        completionPrefix.substring(offset) == name)
      return
    val priorityOffset = baselinePriorityValue - WebSymbol.Priority.NORMAL.value
    LookupElementBuilder.create(symbol?.createPointer() ?: name, name)
      .withLookupStrings(aliases)
      .withIcon(icon?.scaleToHeight(16))
      .withTypeText(typeText, true)
      .withTailText(tailText, true)
      .withBoldness(!deprecated && priority == WebSymbol.Priority.HIGHEST)
      .withStrikeoutness(deprecated)
      .let {
        if (displayName != null)
          it.withPresentableText(displayName)
        else it
      }
      .let {
        if (completeAfterInsert) {
          it.withInsertHandler { insertionContext, _ ->
            insertionContext.setLaterRunnable {
              CodeCompletionHandlerBase(CompletionType.BASIC)
                .invokeCompletion(parameters.originalFile.project, parameters.editor)
            }
          }
        }
        else if (completeAfterChars.isNotEmpty()) {
          it.withInsertHandler { insertionContext, completionItem ->
            if (completeAfterChars.contains(insertionContext.completionChar)) {
              insertionContext.setLaterRunnable {
                CodeCompletionHandlerBase(CompletionType.BASIC)
                  .invokeCompletion(parameters.originalFile.project, parameters.editor)
              }
            }
            else {
              insertHandler?.prepare(insertionContext, completionItem)?.run()
            }
          }
        }
        else {
          it.withInsertHandler { insertionContext, completionItem ->
            insertHandler?.prepare(insertionContext, completionItem)?.run()
          }
        }
      }.let {
        if (completeAfterChars.isNotEmpty())
          it.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        else it
      }.let {
        val priorityValue = if (deprecated) WebSymbol.Priority.LOWEST.value
        else (priority ?: WebSymbol.Priority.NORMAL).value + priorityOffset
        PrioritizedLookupElement.withPriority(it, priorityValue)
      }.let {
        PrioritizedLookupElement.withExplicitProximity(it, proximity ?: 0)
      }.let {
        (if (offset > 0) result.withPrefixMatcher(completionPrefixMatcher.cloneWithPrefix(completionPrefix.substring(offset)))
        else result).addElement(it)
      }
  }

  override fun withName(name: String): WebSymbolCodeCompletionItem =
    copy(name = name)

  override fun withPrefix(prefix: String): WebSymbolCodeCompletionItem =
    if (prefix.isEmpty())
      this
    else
      copy(
        name = prefix + name,
        offset = offset - prefix.length,
        displayName = if (displayName != null) prefix + displayName else null,
        aliases = aliases.asSequence().map { prefix + it }.toSet()
      )

  override fun withOffset(offset: Int): WebSymbolCodeCompletionItem =
    copy(offset = offset)

  override fun withCompleteAfterInsert(completeAfterInsert: Boolean): WebSymbolCodeCompletionItem =
    copy(completeAfterInsert = completeAfterInsert)

  override fun withDisplayName(displayName: String?): WebSymbolCodeCompletionItem =
    copy(displayName = displayName)

  override fun withSymbol(symbol: WebSymbol?): WebSymbolCodeCompletionItem =
    copy(symbol = symbol)

  override fun withPriority(priority: WebSymbol.Priority?): WebSymbolCodeCompletionItem =
    copy(priority = priority)

  override fun withProximity(proximity: Int): WebSymbolCodeCompletionItem =
    copy(proximity = proximity)

  override fun withDeprecated(deprecated: Boolean): WebSymbolCodeCompletionItem =
    copy(deprecated = deprecated)

  override fun withAliasesReplaced(aliases: Set<String>): WebSymbolCodeCompletionItem =
    copy(aliases = aliases)

  override fun withAliasesAdded(aliases: Set<String>): WebSymbolCodeCompletionItem =
    copy(aliases = this.aliases + aliases)

  override fun withAliasAdded(alias: String): WebSymbolCodeCompletionItem =
    copy(aliases = this.aliases + alias)

  override fun withIcon(icon: Icon?): WebSymbolCodeCompletionItem =
    copy(icon = icon)

  override fun withTypeText(typeText: String?): WebSymbolCodeCompletionItem =
    copy(typeText = typeText)

  override fun withTailText(tailText: String?): WebSymbolCodeCompletionItem =
    copy(tailText = tailText)

  override fun withCompleteAfterChar(char: Char): WebSymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + char else emptySet())

  override fun withCompleteAfterCharsAdded(chars: List<Char>): WebSymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + chars else emptySet())

  override fun withInsertHandlerReplaced(insertHandler: WebSymbolCodeCompletionItemInsertHandler?): WebSymbolCodeCompletionItem =
    copy(insertHandler = insertHandler)

  override fun withInsertHandlerAdded(insertHandler: InsertHandler<LookupElement>,
                                      priority: WebSymbol.Priority): WebSymbolCodeCompletionItem =
    withInsertHandlerAdded(WebSymbolCodeCompletionItemInsertHandler.adapt(insertHandler, priority))

  override fun withInsertHandlerAdded(insertHandler: WebSymbolCodeCompletionItemInsertHandler): WebSymbolCodeCompletionItem =
    copy(insertHandler = CompoundInsertHandler.merge(this.insertHandler, insertHandler))

  override fun with(name: String,
                    offset: Int,
                    completeAfterInsert: Boolean,
                    completeAfterChars: Set<Char>,
                    displayName: String?,
                    symbol: WebSymbol?,
                    priority: WebSymbol.Priority?,
                    proximity: Int?,
                    deprecated: Boolean,
                    icon: Icon?,
                    typeText: String?,
                    tailText: String?): WebSymbolCodeCompletionItem =
    copy(name = name, offset = offset, completeAfterInsert = completeAfterInsert,
         completeAfterChars = if (!completeAfterInsert) completeAfterChars else emptySet(),
         displayName = displayName, symbol = symbol, priority = priority, proximity = proximity,
         deprecated = deprecated, icon = icon, typeText = typeText, tailText = tailText)

}