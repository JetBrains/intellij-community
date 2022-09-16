package com.intellij.javascript.web.symbols.patterns

import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolsContainer
import com.intellij.util.containers.Stack

class StaticPattern(val content: String) : WebSymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(content)

  override fun match(owner: WebSymbol?,
                     contextStack: Stack<WebSymbolsContainer>,
                     itemsProvider: ItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    if (content.length <= end - start && params.name.startsWith(content, start))
      listOf(MatchResult(WebSymbol.NameSegment(start, start + content.length)))
    else emptyList()

  override fun getCompletionResults(owner: WebSymbol?,
                                    contextStack: Stack<WebSymbolsContainer>,
                                    itemsProvider: ItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    CompletionResults(WebSymbolCodeCompletionItem.create(content, start))

  override fun toString(): String =
    "\"${content}\""
}