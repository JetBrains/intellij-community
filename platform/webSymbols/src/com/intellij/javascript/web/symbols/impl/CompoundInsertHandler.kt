package com.intellij.javascript.web.symbols.impl

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItemInsertHandler

class CompoundInsertHandler private constructor(val handlers: List<WebSymbolCodeCompletionItemInsertHandler>) : WebSymbolCodeCompletionItemInsertHandler {

  override val priority: WebSymbol.Priority
    get() = WebSymbol.Priority.NORMAL

  override fun prepare(context: InsertionContext, item: LookupElement): Runnable {
    val runnables = handlers.asSequence()
      .sortedByDescending { it.priority }
      .distinct()
      .map { it.prepare(context, item) }
      .toList()

    return Runnable {
      runnables.forEach { it.run() }
    }
  }

  companion object {

    fun merge(a: WebSymbolCodeCompletionItemInsertHandler?, b: WebSymbolCodeCompletionItemInsertHandler?): WebSymbolCodeCompletionItemInsertHandler? {
      if (a == null) return b
      if (b == null) return a

      val handlers = if (a is CompoundInsertHandler) {
        a.handlers.toMutableList()
      }
      else {
        mutableListOf(a)
      }
      if (b is CompoundInsertHandler) {
        handlers.addAll(b.handlers)
      }
      else {
        handlers.add(b)
      }
      return CompoundInsertHandler(handlers)
    }
  }

}