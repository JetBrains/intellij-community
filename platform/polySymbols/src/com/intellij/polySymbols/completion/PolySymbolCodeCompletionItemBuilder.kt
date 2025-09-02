// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import javax.swing.Icon

interface PolySymbolCodeCompletionItemBuilder {
  val displayName: String?
  val offset: Int
  val icon: Icon?
  val typeText: String?
  val tailText: String?

  val completeAfterInsert: Boolean
  val completeAfterChars: Set<Char>
  val priority: PolySymbol.Priority?
  val proximity: Int?

  val apiStatus: PolySymbolApiStatus
  val aliases: Set<String>
  val symbol: PolySymbol?
  val insertHandler: PolySymbolCodeCompletionItemInsertHandler?

  fun displayName(value: String?): PolySymbolCodeCompletionItemBuilder
  fun offset(value: Int): PolySymbolCodeCompletionItemBuilder
  fun icon(value: Icon?): PolySymbolCodeCompletionItemBuilder
  fun typeText(value: String?): PolySymbolCodeCompletionItemBuilder
  fun typeText(provider: () -> String?): PolySymbolCodeCompletionItemBuilder
  fun tailText(value: String?): PolySymbolCodeCompletionItemBuilder

  fun completeAfterInsert(value: Boolean): PolySymbolCodeCompletionItemBuilder
  fun completeAfterChars(value: Set<Char>): PolySymbolCodeCompletionItemBuilder
  fun priority(value: PolySymbol.Priority?): PolySymbolCodeCompletionItemBuilder
  fun proximity(value: Int?): PolySymbolCodeCompletionItemBuilder

  fun apiStatus(value: PolySymbolApiStatus): PolySymbolCodeCompletionItemBuilder
  fun aliases(value: Set<String>): PolySymbolCodeCompletionItemBuilder
  fun symbol(value: PolySymbol?): PolySymbolCodeCompletionItemBuilder
  fun insertHandler(value: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItemBuilder
  fun insertHandler(value: InsertHandler<LookupElement>?): PolySymbolCodeCompletionItemBuilder

  fun build(): PolySymbolCodeCompletionItem
}