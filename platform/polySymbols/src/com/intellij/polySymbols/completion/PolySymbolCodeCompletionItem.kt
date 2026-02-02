// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.completion.impl.CodeCompletionPolySymbolWithDocumentation
import com.intellij.polySymbols.completion.impl.PolySymbolCodeCompletionItemImpl
import com.intellij.polySymbols.completion.impl.PsiSourcedCodeCompletionPolySymbolWithDocumentation
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.psi.PsiElement
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
  val typeTextGreyed: Boolean
  val tailText: String?
  val tailTextGreyed: Boolean
  val caseSensitive: Boolean

  @get:JvmName("isCompleteAfterInsert")
  val completeAfterInsert: Boolean
  val completeAfterChars: Set<Char>
  val priority: PolySymbol.Priority?
  val proximity: Int?

  val apiStatus: PolySymbolApiStatus
  val aliases: Set<String>
  val symbol: PolySymbol?
  val insertHandler: PolySymbolCodeCompletionItemInsertHandler?

  val asyncCustomizers: List<PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem>

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

  fun withTypeText(typeText: String?, greyed: Boolean): PolySymbolCodeCompletionItem

  fun withTailText(tailText: String?): PolySymbolCodeCompletionItem

  fun withTailText(tailText: String?, greyed: Boolean): PolySymbolCodeCompletionItem

  fun withAsyncCustomizer(asyncCustomizer: PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem): PolySymbolCodeCompletionItem

  fun withCaseSensitive(value: Boolean): PolySymbolCodeCompletionItem

  fun withCompleteAfterChar(char: Char): PolySymbolCodeCompletionItem

  fun withCompleteAfterCharsAdded(chars: List<Char>): PolySymbolCodeCompletionItem

  fun withInsertHandlerReplaced(insertHandler: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItem

  fun withInsertHandlerAdded(
    insertHandler: InsertHandler<LookupElement>,
    priority: PolySymbol.Priority = PolySymbol.Priority.NORMAL,
  ): PolySymbolCodeCompletionItem

  fun withInsertHandlerAdded(insertHandler: PolySymbolCodeCompletionItemInsertHandler): PolySymbolCodeCompletionItem

  fun withCompletionRestartedOnPrefixChange(prefix: String): PolySymbolCodeCompletionItem

  fun withCompletionRestartedOnAnyPrefixChange(): PolySymbolCodeCompletionItem

  companion object {

    @JvmStatic
    @JvmOverloads
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
    @JvmOverloads
    fun builder(
      name: String,
      offset: Int = 0,
      symbol: PolySymbol? = null,
    ): PolySymbolCodeCompletionItemBuilder =
      PolySymbolCodeCompletionItemImpl.BuilderImpl(name, offset, symbol)

    @JvmStatic
    fun getPolySymbol(lookupElement: LookupElement): PolySymbol? =
      (lookupElement.`object` as? Pointer<*>)
        ?.dereference()
        ?.let {
          when (it) {
            is CodeCompletionPolySymbolWithDocumentation -> it.delegate
            is PsiSourcedCodeCompletionPolySymbolWithDocumentation -> it.delegate
            is PolySymbol -> it
            else -> null
          }
        }

    @JvmStatic
    fun getPsiElement(lookupElement: LookupElement): PsiElement? =
      lookupElement.psiElement
      ?: getPolySymbol(lookupElement)
        ?.let { it as? PsiSourcedPolySymbol }
        ?.source

  }

}