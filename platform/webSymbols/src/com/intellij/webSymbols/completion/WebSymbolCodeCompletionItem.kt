// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.query.WebSymbolDefaultIconProvider
import com.intellij.webSymbols.completion.impl.WebSymbolCodeCompletionItemImpl
import org.jetbrains.annotations.ApiStatus.NonExtendable
import javax.swing.Icon

/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
@NonExtendable
interface WebSymbolCodeCompletionItem {
  val name: String
  val displayName: String?
  val offset: Int
  val icon: Icon?
  val typeText: String?
  val tailText: String?

  @get:JvmName("isCompleteAfterInsert")
  val completeAfterInsert: Boolean
  val completeAfterChars: Set<Char>
  val priority: WebSymbol.Priority?
  val proximity: Int?

  @get:JvmName("isDeprecated")
  val deprecated: Boolean
  val aliases: Set<String>
  val symbol: WebSymbol?
  val insertHandler: WebSymbolCodeCompletionItemInsertHandler?

  fun withPrefix(prefix: String): WebSymbolCodeCompletionItem

  fun addToResult(parameters: CompletionParameters,
                  result: CompletionResultSet,
                  baselinePriorityValue: Double = WebSymbol.Priority.NORMAL.value)

  fun withName(name: String): WebSymbolCodeCompletionItem

  fun withOffset(offset: Int): WebSymbolCodeCompletionItem

  fun withCompleteAfterInsert(completeAfterInsert: Boolean): WebSymbolCodeCompletionItem

  fun withDisplayName(displayName: String?): WebSymbolCodeCompletionItem

  fun withSymbol(symbol: WebSymbol?): WebSymbolCodeCompletionItem

  fun withPriority(priority: WebSymbol.Priority?): WebSymbolCodeCompletionItem

  fun withProximity(proximity: Int): WebSymbolCodeCompletionItem

  fun withDeprecated(deprecated: Boolean): WebSymbolCodeCompletionItem

  fun withAliasesReplaced(aliases: Set<String>): WebSymbolCodeCompletionItem

  fun withAliasesAdded(aliases: Set<String>): WebSymbolCodeCompletionItem

  fun withAliasAdded(alias: String): WebSymbolCodeCompletionItem

  fun withIcon(icon: Icon?): WebSymbolCodeCompletionItem

  fun withTypeText(typeText: String?): WebSymbolCodeCompletionItem

  fun withTailText(tailText: String?): WebSymbolCodeCompletionItem

  fun withCompleteAfterChar(char: Char): WebSymbolCodeCompletionItem

  fun withCompleteAfterCharsAdded(chars: List<Char>): WebSymbolCodeCompletionItem

  fun withInsertHandlerReplaced(insertHandler: WebSymbolCodeCompletionItemInsertHandler?): WebSymbolCodeCompletionItem

  fun withInsertHandlerAdded(insertHandler: InsertHandler<LookupElement>,
                             priority: WebSymbol.Priority = WebSymbol.Priority.NORMAL): WebSymbolCodeCompletionItem

  fun withInsertHandlerAdded(insertHandler: WebSymbolCodeCompletionItemInsertHandler): WebSymbolCodeCompletionItem

  fun with(name: String = this.name,
           offset: Int = this.offset,
           completeAfterInsert: Boolean = this.completeAfterInsert,
           completeAfterChars: Set<Char> = this.completeAfterChars,
           displayName: String? = this.displayName,
           symbol: WebSymbol? = this.symbol,
           priority: WebSymbol.Priority? = this.priority,
           proximity: Int? = this.proximity,
           deprecated: Boolean = this.deprecated,
           icon: Icon? = this.icon,
           typeText: String? = this.typeText,
           tailText: String? = this.tailText): WebSymbolCodeCompletionItem

  companion object {
    @JvmStatic
    fun create(name: String,
               offset: Int = 0,
               completeAfterInsert: Boolean = false,
               completeAfterChars: Set<Char> = emptySet(),
               displayName: String? = null,
               symbol: WebSymbol? = null,
               priority: WebSymbol.Priority? = symbol?.priority,
               proximity: Int? = symbol?.proximity,
               deprecated: Boolean = symbol?.deprecated ?: false,
               aliases: Set<String> = emptySet(),
               icon: Icon? = symbol?.let {
                 it.icon
                 ?: it.origin.defaultIcon
                 ?: WebSymbolDefaultIconProvider.get(it.namespace, it.kind)
               },
               typeText: String? = null,
               tailText: String? = null,
               insertHandler: WebSymbolCodeCompletionItemInsertHandler? = null): WebSymbolCodeCompletionItem =
      WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, if (!completeAfterInsert) completeAfterChars else emptySet(),
                                      displayName, symbol, priority, proximity,
                                      deprecated, aliases, icon, typeText, tailText, insertHandler)

    @JvmStatic
    fun getPsiElement(lookupElement: LookupElement): PsiElement? =
      lookupElement.psiElement
      ?: (lookupElement.`object` as? Pointer<*>)
        ?.dereference()
        ?.let { it as? PsiSourcedWebSymbol }
        ?.source

  }

}