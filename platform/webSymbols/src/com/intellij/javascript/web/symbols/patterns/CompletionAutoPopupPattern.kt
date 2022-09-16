package com.intellij.javascript.web.symbols.patterns

import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.javascript.web.symbols.WebSymbolsContainer
import com.intellij.javascript.web.symbols.hideFromCompletion
import com.intellij.util.containers.Stack

class CompletionAutoPopupPattern(val isSticky: Boolean) : WebSymbolsPattern() {

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun match(owner: WebSymbol?,
                     contextStack: Stack<WebSymbolsContainer>,
                     itemsProvider: ItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    listOf(MatchResult(WebSymbol.NameSegment(start, start)))

  override fun getCompletionResults(owner: WebSymbol?,
                                    contextStack: Stack<WebSymbolsContainer>,
                                    itemsProvider: ItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    if (itemsProvider == null || itemsProvider.delegate?.hideFromCompletion == true) {
      CompletionResults(emptyList(), true)
    }
    else {
      CompletionResults(WebSymbolCodeCompletionItem.create("", start, true, displayName = "…"))
    }

  override fun toString(): String {
    return "…" + (if (isSticky) "$" else "")
  }
}