// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import javax.swing.Icon

interface WebSymbolCodeCompletionItemBuilder {
  fun displayName(value: String?): WebSymbolCodeCompletionItemBuilder
  fun offset(value: Int): WebSymbolCodeCompletionItemBuilder
  fun icon(value: Icon?): WebSymbolCodeCompletionItemBuilder
  fun typeText(value: String?): WebSymbolCodeCompletionItemBuilder
  fun typeText(provider: () -> String?): WebSymbolCodeCompletionItemBuilder
  fun tailText(value: String?): WebSymbolCodeCompletionItemBuilder

  fun completeAfterInsert(value: Boolean): WebSymbolCodeCompletionItemBuilder
  fun completeAfterChars(value: Set<Char>): WebSymbolCodeCompletionItemBuilder
  fun priority(value: WebSymbol.Priority?): WebSymbolCodeCompletionItemBuilder
  fun proximity(value: Int?): WebSymbolCodeCompletionItemBuilder

  fun apiStatus(value: WebSymbolApiStatus): WebSymbolCodeCompletionItemBuilder
  fun aliases(value: Set<String>): WebSymbolCodeCompletionItemBuilder
  fun symbol(value: WebSymbol?): WebSymbolCodeCompletionItemBuilder
  fun insertHandler(value: WebSymbolCodeCompletionItemInsertHandler?): WebSymbolCodeCompletionItemBuilder
}