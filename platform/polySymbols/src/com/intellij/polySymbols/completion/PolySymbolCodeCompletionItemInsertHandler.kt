// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.polySymbols.PolySymbol

interface PolySymbolCodeCompletionItemInsertHandler {

  val priority: PolySymbol.Priority
    get() = PolySymbol.Priority.NORMAL

  fun prepare(context: InsertionContext, item: LookupElement, completeAfterInsert: Boolean): Runnable?

  companion object {

    internal fun adapt(
      insertHandler: InsertHandler<LookupElement>,
      priority: PolySymbol.Priority,
    ): PolySymbolCodeCompletionItemInsertHandler =
      object : PolySymbolCodeCompletionItemInsertHandler {

        override val priority: PolySymbol.Priority
          get() = priority

        override fun prepare(context: InsertionContext, item: LookupElement, completeAfterInsert: Boolean) =
          if (!completeAfterInsert)
            Runnable { insertHandler.handleInsert(context, item) }
          else null

      }

  }

}