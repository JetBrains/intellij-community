package com.intellij.javascript.web.symbols.patterns

import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.javascript.web.symbols.WebSymbolsContainer
import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import java.util.regex.Pattern

class RegExpPattern(private val regex: String, private val caseSensitive: Boolean = false) : WebSymbolsPattern() {
  private val pattern: Pattern by lazy(LazyThreadSafetyMode.NONE) {
    if (caseSensitive)
      Pattern.compile(regex)
    else
      Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
  }

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(getPatternCompletablePrefix(regex))

  override fun isStaticAndRequired(): Boolean = false

  override fun match(owner: WebSymbol?,
                     contextStack: Stack<WebSymbolsContainer>,
                     itemsProvider: ItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> {
    val matcher = pattern.matcher(CharSequenceSubSequence(params.name, start, end))
    return if (matcher.find(0) && matcher.start() == 0)
      listOf(MatchResult(WebSymbol.NameSegment(start, start + matcher.end(),
                                               owner?.let { listOf(it) } ?: emptyList(),
                                               matchScore = getPatternCompletablePrefix(regex).length)))
    else emptyList()
  }

  override fun getCompletionResults(owner: WebSymbol?,
                                    contextStack: Stack<WebSymbolsContainer>,
                                    itemsProvider: ItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    getPatternCompletablePrefix(regex)
      .takeIf { it.isNotBlank() }
      ?.let {
        CompletionResults(WebSymbolCodeCompletionItem.create(it, start, true, displayName = "$it…", symbol = owner))
      }
    ?: CompletionResults(WebSymbolCodeCompletionItem.create("", start, true, displayName = "…", symbol = owner))


  override fun toString(): String =
    "\\${regex}\\"
}