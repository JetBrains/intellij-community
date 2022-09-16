package com.intellij.javascript.web.symbols.patterns

import com.intellij.javascript.web.symbols.*
import com.intellij.javascript.web.symbols.impl.WebSymbolCodeCompletionItemImpl
import com.intellij.util.containers.Stack

abstract class WebSymbolsPattern {

  companion object {

    private val SPECIAL_CHARS = setOf('[', '.', '\\', '^', '$', '(', '+')
    private val SPECIAL_CHARS_ONE_BACK = setOf('?', '*', '{')
    internal const val SPECIAL_MATCHED_CONTRIB = "\$special$"

    fun getPatternCompletablePrefix(pattern: String?): String {
      if (pattern == null || pattern.contains('|')) return ""
      for (i in 0..pattern.length) {
        val char = pattern[i]
        if (SPECIAL_CHARS.contains(char)) {
          return pattern.substring(0 until i)
        }
        else if (SPECIAL_CHARS_ONE_BACK.contains(char)) {
          return if (i < 1) "" else pattern.substring(0 until i - 1)
        }
      }
      return pattern
    }

    fun <T> withPrevMatchContext(contextStack: Stack<WebSymbolsContainer>,
                                 prevResult: List<WebSymbol.NameSegment>?,
                                 action: () -> T): T =
      if (prevResult == null) {
        action()
      }
      else {
        val additionalContext = prevResult
          .flatMap { it.symbols }
          .flatMap { it.contextContainers }
        contextStack.addAll(additionalContext)
        try {
          action()
        }
        finally {
          repeat(additionalContext.size) { contextStack.pop() }
        }
      }

    @JvmStatic
    protected fun WebSymbolCodeCompletionItem.withStopSequencePatternEvaluation(stop: Boolean): WebSymbolCodeCompletionItem =
      if ((this as WebSymbolCodeCompletionItemImpl).stopSequencePatternEvaluation != stop)
        this.copy(stopSequencePatternEvaluation = stop)
      else this

    @JvmStatic
    protected val WebSymbolCodeCompletionItem.stopSequencePatternEvaluation
      get() = (this as WebSymbolCodeCompletionItemImpl).stopSequencePatternEvaluation

  }

  abstract fun getStaticPrefixes(): Sequence<String>

  open fun isStaticAndRequired(): Boolean = true

  fun match(owner: WebSymbol?,
            context: Stack<WebSymbolsContainer>,
            name: String,
            params: WebSymbolsNameMatchQueryParams): List<MatchResult> =
    match(owner, context, null, MatchParameters(name, params), 0, name.length)
      .map { it.removeEmptySegments() }

  fun getCompletionResults(owner: WebSymbol?,
                           context: Stack<WebSymbolsContainer>,
                           name: String,
                           params: WebSymbolsCodeCompletionQueryParams): List<WebSymbolCodeCompletionItem> =
    getCompletionResults(owner, Stack(context), null,
                         CompletionParameters(name, params), 0, name.length).items

  internal abstract fun match(owner: WebSymbol?,
                              contextStack: Stack<WebSymbolsContainer>,
                              itemsProvider: ItemsProvider?,
                              params: MatchParameters, start: Int, end: Int): List<MatchResult>

  internal abstract fun getCompletionResults(owner: WebSymbol?,
                                             contextStack: Stack<WebSymbolsContainer>,
                                             itemsProvider: ItemsProvider?,
                                             params: CompletionParameters, start: Int, end: Int): CompletionResults

  interface ItemsProvider {
    @JvmDefault
    fun getSymbolTypes(context: WebSymbol?): Set<WebSymbol.SymbolType> =
      emptySet()

    val delegate: WebSymbol?

    fun codeCompletion(name: String,
                       position: Int,
                       contextStack: Stack<WebSymbolsContainer>,
                       registry: WebSymbolsRegistry): List<WebSymbolCodeCompletionItem>

    fun matchName(name: String, contextStack: Stack<WebSymbolsContainer>, registry: WebSymbolsRegistry): List<WebSymbol>

  }

  class MatchResult(
    val segments: List<WebSymbol.NameSegment>
  ) {

    constructor(segment: WebSymbol.NameSegment) : this(listOf(segment))

    init {
      assert(segments.isNotEmpty())
    }

    val start: Int = segments.first().start
    val end: Int = segments.last().end

    fun prefixedWith(segments: List<WebSymbol.NameSegment>): MatchResult =
      if (segments.isNotEmpty()) MatchResult(segments + this.segments) else this

    fun prefixedWith(prevResult: MatchResult?): MatchResult =
      prevResult?.let { MatchResult(it.segments + this.segments) }
      ?: this

    fun applyToSegments(vararg contributions: WebSymbol,
                        deprecated: Boolean? = null,
                        priority: WebSymbol.Priority? = null,
                        proximity: Int? = null): MatchResult =
      if (deprecated != null || priority != null || proximity != null || contributions.isNotEmpty())
        MatchResult(
          segments.map {
            it.applyProperties(deprecated = deprecated, priority = priority, proximity = proximity, symbols = contributions.toList())
          })
      else
        this

    fun removeEmptySegments(): MatchResult =
      MatchResult(
        segments.filter {
          it.start != it.end
          || it.problem != null
          || it.symbols.isNotEmpty()
          || it.deprecated
          || it.proximity != null
        }.ifEmpty { listOf(segments.first()) })

    override fun toString(): String {
      return segments.toString()
    }

  }

  open class MatchParameters(val name: String,
                             val registry: WebSymbolsRegistry) {

    constructor(name: String, params: WebSymbolsNameMatchQueryParams)
      : this(name, params.registry)

    val framework: String? get() = registry.framework

    override fun toString(): String =
      "match: $name (framework: $framework)"
  }

  class CompletionParameters(name: String,
                             registry: WebSymbolsRegistry,
                             val position: Int) : MatchParameters(name, registry) {
    constructor(name: String, params: WebSymbolsCodeCompletionQueryParams)
      : this(name, params.registry, params.position)

    override fun toString(): String =
      "complete: $name (position: $position, framework: $framework)"

    fun withPosition(position: Int): CompletionParameters =
      CompletionParameters(name, registry, position)
  }

  data class CompletionResults(val items: List<WebSymbolCodeCompletionItem>,
                               val required: Boolean = true) {

    constructor(item: WebSymbolCodeCompletionItem,
                required: Boolean = true,
                stop: Boolean = false) : this(listOf(item.withStopSequencePatternEvaluation(stop)), required)

    val stop: Boolean get() = items.any { it.stopSequencePatternEvaluation }

  }

}