// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus
import javax.swing.Icon

interface PolySymbolCodeCompletionItemBuilder {
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
  fun insertHandler(value: WebSymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItemBuilder
}