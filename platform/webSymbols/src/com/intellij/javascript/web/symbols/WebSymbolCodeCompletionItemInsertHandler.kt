package com.intellij.javascript.web.symbols

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

interface WebSymbolCodeCompletionItemInsertHandler {

  val priority: WebSymbol.Priority

  fun prepare(context: InsertionContext, item: LookupElement): Runnable

  companion object {

    fun adapt(insertHandler: InsertHandler<LookupElement>, priority: WebSymbol.Priority): WebSymbolCodeCompletionItemInsertHandler =
      object : WebSymbolCodeCompletionItemInsertHandler {

        override val priority: WebSymbol.Priority
          get() = priority

        override fun prepare(context: InsertionContext, item: LookupElement) =
          Runnable { insertHandler.handleInsert(context, item) }

      }

  }

}