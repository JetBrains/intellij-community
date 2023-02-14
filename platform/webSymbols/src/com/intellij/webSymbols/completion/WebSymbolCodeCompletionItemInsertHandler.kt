// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.webSymbols.WebSymbol

interface WebSymbolCodeCompletionItemInsertHandler {

  val priority: WebSymbol.Priority

  fun prepare(context: InsertionContext, item: LookupElement): Runnable

  companion object {

    internal fun adapt(insertHandler: InsertHandler<LookupElement>,
                       priority: WebSymbol.Priority): WebSymbolCodeCompletionItemInsertHandler =
      object : WebSymbolCodeCompletionItemInsertHandler {

        override val priority: WebSymbol.Priority
          get() = priority

        override fun prepare(context: InsertionContext, item: LookupElement) =
          Runnable { insertHandler.handleInsert(context, item) }

      }

  }

}