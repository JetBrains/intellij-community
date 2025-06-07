// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.completion.impl.PolySymbolCodeCompletionItemImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.NonExtendable
import javax.swing.Icon

/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
@NonExtendable
interface PolySymbolCodeCompletionItem {
  val name: String
  val displayName: String?
  val offset: Int
  val icon: Icon?
  val typeText: String?
  val tailText: String?

  @get:JvmName("isCompleteAfterInsert")
  val completeAfterInsert: Boolean
  val completeAfterChars: Set<Char>
  val priority: PolySymbol.Priority?
  val proximity: Int?

  val apiStatus: PolySymbolApiStatus
  val aliases: Set<String>
  val symbol: PolySymbol?
  val insertHandler: PolySymbolCodeCompletionItemInsertHandler?

  fun withPrefix(prefix: String): PolySymbolCodeCompletionItem

  fun addToResult(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    baselinePriorityValue: Double = PolySymbol.Priority.NORMAL.value,
  )

  fun withName(name: String): PolySymbolCodeCompletionItem

  fun withOffset(offset: Int): PolySymbolCodeCompletionItem

  fun withCompleteAfterInsert(completeAfterInsert: Boolean): PolySymbolCodeCompletionItem

  fun withDisplayName(displayName: String?): PolySymbolCodeCompletionItem

  fun withSymbol(symbol: PolySymbol?): PolySymbolCodeCompletionItem

  fun withPriority(priority: PolySymbol.Priority?): PolySymbolCodeCompletionItem

  fun withProximity(proximity: Int): PolySymbolCodeCompletionItem

  fun withApiStatus(apiStatus: PolySymbolApiStatus): PolySymbolCodeCompletionItem

  fun withAliasesReplaced(aliases: Set<String>): PolySymbolCodeCompletionItem

  fun withAliasesAdded(aliases: Set<String>): PolySymbolCodeCompletionItem

  fun withAliasAdded(alias: String): PolySymbolCodeCompletionItem

  fun withIcon(icon: Icon?): PolySymbolCodeCompletionItem

  fun withTypeText(typeText: String?): PolySymbolCodeCompletionItem

  fun withTypeText(typeTextProvider: () -> String?): PolySymbolCodeCompletionItem

  fun withTailText(tailText: String?): PolySymbolCodeCompletionItem

  fun withCompleteAfterChar(char: Char): PolySymbolCodeCompletionItem

  fun withCompleteAfterCharsAdded(chars: List<Char>): PolySymbolCodeCompletionItem

  fun withInsertHandlerReplaced(insertHandler: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItem

  fun withInsertHandlerAdded(
    insertHandler: InsertHandler<LookupElement>,
    priority: PolySymbol.Priority = PolySymbol.Priority.NORMAL,
  ): PolySymbolCodeCompletionItem

  fun withInsertHandlerAdded(insertHandler: PolySymbolCodeCompletionItemInsertHandler): PolySymbolCodeCompletionItem

  fun with(
    name: String = this.name,
    offset: Int = this.offset,
    completeAfterInsert: Boolean = this.completeAfterInsert,
    completeAfterChars: Set<Char> = this.completeAfterChars,
    displayName: String? = this.displayName,
    symbol: PolySymbol? = this.symbol,
    priority: PolySymbol.Priority? = this.priority,
    proximity: Int? = this.proximity,
    apiStatus: PolySymbolApiStatus = this.apiStatus,
    icon: Icon? = this.icon,
    typeText: String? = null,
    tailText: String? = this.tailText,
  ): PolySymbolCodeCompletionItem

  companion object {

    @ApiStatus.Internal
    fun create(
      name: String,
      offset: Int = 0,
      completeAfterInsert: Boolean = false,
      completeAfterChars: Set<Char>? = null,
      displayName: String? = null,
      symbol: PolySymbol? = null,
      priority: PolySymbol.Priority? = null,
      proximity: Int? = null,
      apiStatus: PolySymbolApiStatus? = null,
      aliases: Set<String>? = null,
      icon: Icon? = null,
      typeText: String? = null,
      tailText: String? = null,
      insertHandler: PolySymbolCodeCompletionItemInsertHandler? = null,
    ): PolySymbolCodeCompletionItem =
      create(name, offset, symbol) {
        completeAfterInsert(completeAfterInsert)
        completeAfterChars?.let { completeAfterChars(it) }
        displayName(displayName)
        priority?.let { priority(it) }
        proximity?.let { proximity(it) }
        apiStatus?.let { apiStatus(it) }
        aliases?.let { aliases(it) }
        icon?.let { icon(it) }
        typeText?.let { typeText(it) }
        tailText?.let { tailText(it) }
        insertHandler?.let { insertHandler(it) }
      }

    @JvmStatic
    fun create(
      name: String,
      offset: Int = 0,
      symbol: PolySymbol? = null,
      builder: (PolySymbolCodeCompletionItemBuilder.() -> Unit)? = null,
    ): PolySymbolCodeCompletionItem =
      PolySymbolCodeCompletionItemImpl.BuilderImpl(name, offset, symbol)
        .also { builder?.invoke(it) }
        .build()

    @JvmStatic
    fun getPsiElement(lookupElement: LookupElement): PsiElement? =
      lookupElement.psiElement
      ?: (lookupElement.`object` as? Pointer<*>)
        ?.dereference()
        ?.let { it as? PsiSourcedPolySymbol }
        ?.source

  }

}