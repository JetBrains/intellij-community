// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.WebSymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.utils.coalesceWith
import com.intellij.webSymbols.utils.isCritical
import kotlin.math.max
import kotlin.math.min

/**
 * Complex pattern matches any of the provided patterns
 * and allows for high level of customization.
 */
internal class ComplexPattern(private val configProvider: ComplexPatternConfigProvider) : WebSymbolsPattern() {

  private val patterns: List<WebSymbolsPattern>
    get() = configProvider.getPatterns()

  override fun getStaticPrefixes(): Sequence<String> =
    patterns
      .asSequence()
      .flatMap { it.getStaticPrefixes() }

  override fun isStaticAndRequired(): Boolean =
    configProvider.isStaticAndRequired

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    process(scopeStack, params.queryExecutor) { patterns, newSymbolsResolver, apiStatus,
                                                isRequired, priority, proximity, repeats, unique ->
      performPatternMatch(params, start, end, patterns, repeats, unique, scopeStack, newSymbolsResolver)
        .let { matchResults ->
          if (!isRequired)
            matchResults.filter { matchResult ->
              matchResult.segments
                .all { it.problem != WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART }
            }
          else
            matchResults
        }
        .postProcess(owner, apiStatus, priority, proximity)
        .let { matchResults ->
          if (!isRequired)
            matchResults + MatchResult(WebSymbolNameSegment(start, start))
          else if (matchResults.isEmpty())
            listOf(MatchResult(WebSymbolNameSegment(start, start, problem = WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART)))
          else
            matchResults
        }
    }

  override fun list(owner: WebSymbol?,
                    scopeStack: Stack<WebSymbolsScope>,
                    symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                    params: ListParameters): List<ListResult> =
    process(scopeStack, params.queryExecutor) { patterns, newSymbolsResolver, apiStatus,
                                                isRequired, priority, proximity, repeats, _ ->
      if (repeats)
        if (isRequired)
          emptyList()
        else
          listOf(ListResult("", WebSymbolNameSegment(0, 0)))
      else
        patterns
          .flatMap { it.list(null, scopeStack, newSymbolsResolver, params) }
          .groupBy { it.name }
          .values
          .flatMap { it.postProcess(owner, apiStatus, priority, proximity) }
          .let {
            if (!isRequired)
              it + ListResult("", WebSymbolNameSegment(0, 0))
            else
              it
          }

    }

  override fun complete(owner: WebSymbol?,
                        scopeStack: Stack<WebSymbolsScope>,
                        symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                        params: CompletionParameters,
                        start: Int,
                        end: Int): CompletionResults =
    process(scopeStack, params.queryExecutor) { patterns, newSymbolsResolver, apiStatus,
                                                isRequired, priority, proximity, repeats, unique ->
      var staticPrefixes: Set<String> = emptySet()

      val runs = if (start == end) {
        listOf(CompletionResultRun(start, start, emptySet(), emptyList()))
      }
      else if (repeats && getStaticPrefixes().filter { it != "" }.toSet().also { staticPrefixes = it }.isNotEmpty()) {
        repeatingPatternMatch(start, end, params, staticPrefixes, scopeStack, patterns, newSymbolsResolver, false)
          .flatMap { (_, matchSegments) ->
            buildCompletionResultRuns(matchSegments, params)
          }
      }
      else {
        listOf(CompletionResultRun(start, end, emptySet(), emptyList()))
      }

      val patternsItems = runs.flatMap { (localStart, localEnd, prevNames, prevMatchScope) ->
        val defaultSource = symbolsResolver?.delegate ?: scopeStack.peek() as? WebSymbol
        withPrevMatchScope(scopeStack, prevMatchScope) {
          patterns.flatMap { pattern ->
            pattern.complete(null, scopeStack, newSymbolsResolver, params, localStart, localEnd)
              .items
              .asSequence()
              .let { items ->
                if (repeats && unique && prevNames.isNotEmpty()) {
                  items.filter {
                    !prevNames.contains(params.name.substring(localStart, min(max(it.offset, localStart), localEnd)) + it.name)
                  }
                }
                else items
              }
              .let { items ->
                items.map { item ->
                  item.with(
                    priority = priority ?: item.priority,
                    proximity = proximity?.let { (item.proximity ?: 0) + proximity } ?: item.proximity,
                    apiStatus = apiStatus.coalesceWith(item.apiStatus),
                    symbol = item.symbol ?: defaultSource,
                    completeAfterChars = (if (repeats) getStaticPrefixes().mapNotNull { it.getOrNull(0) }.toSet() else emptySet())
                                         + item.completeAfterChars
                  )
                }
              }
          }
        }
      }
      CompletionResults(patternsItems, isRequired)
    }

  private fun <T> process(scopeStack: Stack<WebSymbolsScope>,
                          queryExecutor: WebSymbolsQueryExecutor,
                          action: (patterns: List<WebSymbolsPattern>,
                                   symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                   patternApiStatus: WebSymbolApiStatus?,
                                   patternRequired: Boolean,
                                   patternPriority: WebSymbol.Priority?,
                                   patternProximity: Int?,
                                   patternRepeat: Boolean,
                                   patternUnique: Boolean) -> T): T {
    val options = configProvider.getOptions(queryExecutor, scopeStack)

    val additionalScope = options.additionalScope
    if (additionalScope != null) {
      scopeStack.push(additionalScope)
    }
    try {
      return action(patterns, options.symbolsResolver, options.apiStatus, options.isRequired, options.priority,
                    options.proximity, options.repeats, options.unique)
    }
    finally {
      if (additionalScope != null) {
        scopeStack.pop()
      }
    }
  }

  private fun <T : MatchResult> List<T>.postProcess(
    owner: WebSymbol?,
    apiStatus: WebSymbolApiStatus?,
    priority: WebSymbol.Priority?,
    proximity: Int?
  ): List<T> =
    // We need to filter and select match results separately for each length of the match
    let { matchResults ->
      if (matchResults.size == 1)
        listOf(matchResults)
      else
        matchResults.groupBy { it.end }
          .values
    }
      .flatMap {
        it.selectShortestWithoutProblems()
          .map { matchResult ->
            if (owner != null && owner.kind != SPECIAL_MATCHED_CONTRIB) {
              matchResult.addOwner(owner)
            }
            else matchResult
          }
          .selectBest(MatchResult::segments, { item ->
            item.segments.asSequence().mapNotNull { it.priority }.maxOrNull() ?: WebSymbol.Priority.NORMAL
          }, { false })
      }
      .let { matchResults ->
        if (matchResults.isNotEmpty() && (apiStatus.isDeprecatedOrObsolete() || priority != null || proximity != null))
          matchResults.map { it.applyToSegments(apiStatus = apiStatus, priority = priority, proximity = proximity) }
        else matchResults
      }

  private fun performPatternMatch(params: MatchParameters,
                                  start: Int,
                                  end: Int,
                                  patterns: List<WebSymbolsPattern>,
                                  repeats: Boolean,
                                  unique: Boolean,
                                  contextStack: Stack<WebSymbolsScope>,
                                  newSymbolsResolver: WebSymbolsPatternSymbolsResolver?): List<MatchResult> {
    // shortcut
    if (start == end) {
      // This won't work for nested patterns, but at least allow for one level of empty
      // static pattern
      return patterns.asSequence()
        .filter { it is StaticPattern && it.content.isEmpty() }
        .flatMap {
          it.match(null, contextStack, newSymbolsResolver, params, start, end)
        }
        .toList()
    }
    val staticPrefixes: Set<String> = getStaticPrefixes().filter { it != "" }.toSet()
    return if (repeats && staticPrefixes.isNotEmpty()) {
      repeatingPatternMatch(start, end, params, staticPrefixes, contextStack, patterns, newSymbolsResolver, unique)
        .mapNotNull { it.second.reduceOrNull { a, b -> b.prefixedWith(a) } }
    }
    else {
      patterns.flatMap {
        it.match(null, contextStack, newSymbolsResolver, params, start, end)
      }
    }
  }

  private fun repeatingPatternMatch(start: Int,
                                    end: Int,
                                    params: MatchParameters,
                                    staticPrefixes: Set<String>,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    patterns: List<WebSymbolsPattern>,
                                    newSymbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                    unique: Boolean): SmartList<Pair<Int, List<MatchResult>>> {
    val complete = SmartList<Pair<Int, List<MatchResult>>>()
    val toProcess = Stack<Pair<List<MatchResult>, List<CharSequence>>?>(null)
    while (toProcess.isNotEmpty()) {
      val prevResult = toProcess.pop()
      val matchStart = prevResult?.first?.lastOrNull()?.end ?: start
      val matchEnd = findMatchEnd(params.name, staticPrefixes, matchStart, end)

      withPrevMatchScope(scopeStack, prevResult?.first?.flatMap { it.segments }) {
        for (pattern in patterns) {
          pattern.match(null, scopeStack, newSymbolsResolver, params, matchStart, matchEnd).forEach {
            val prevMatchedSegments: List<CharSequence>
            var matchResult: MatchResult = it
            if (unique) {
              val cur = CharSequenceSubSequence(params.name, matchResult.start, matchResult.end)
              for (prev in prevResult?.second ?: emptyList()) {
                if (StringUtil.equals(prev, cur)) {
                  matchResult = MatchResult(
                    matchResult.segments.map { segment ->
                      if (segment.problem == null || segment.problem == WebSymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL)
                        segment.copy(problem = WebSymbolNameSegment.MatchProblem.DUPLICATE)
                      else segment
                    }
                  )
                  break
                }
              }
              prevMatchedSegments = (prevResult?.second ?: emptyList()) + cur
            }
            else {
              prevMatchedSegments = emptyList()
            }
            if (matchResult.end in (matchStart + 1) until end) {
              toProcess.push(Pair((prevResult?.first ?: emptyList()) + matchResult, prevMatchedSegments))
            }
            else {
              complete.add(Pair(matchStart, (prevResult?.first ?: emptyList()) + matchResult))
            }
          }
        }
      }
    }
    val result = SmartList<Pair<Int, List<MatchResult>>>()
    result.addAll(complete)

    // Add variants of section matches without trailing segments with problems
    complete.forEach { (_, sections) ->
      sections.indices.reversed().takeWhile { index ->
        sections[index].segments.any { it.problem.isCritical }
      }.forEach { index ->
        if (index > 0) {
          val variant = sections.subList(0, index)
          result.add(Pair(variant.last().start, variant))
        }
      }
    }

    // Add unfinished, remaining part might be consumed in following patterns
    toProcess.forEach {
      it?.first?.takeIf { list -> list.isNotEmpty() }?.let { matchSegments ->
        result.add(Pair(matchSegments.last().start, matchSegments))
      }
    }
    return result
  }

  private fun findMatchEnd(name: String, staticPrefixes: Set<String>, start: Int, end: Int): Int {
    val toMatch = CharSequenceSubSequence(name, start, end)

    return staticPrefixes.asSequence()
             .map { toMatch.indexOf(it, 1).let { ind -> if (ind < 0) end else start + ind } }
             .minOrNull() ?: end
  }

  private fun <T : MatchResult> List<T>.selectShortestWithoutProblems(): List<T> {
    if (this.size == 1) return this
    val result = SmartList(this)
    val matchEnd = get(0).end
    assert(all { it.end == matchEnd }) { this.toString() }

    var checkStart = 0
    while (checkStart < matchEnd && result.size > 1) {
      var bestStart = 0
      var bestEnd = matchEnd
      val list = result.toList()
      result.clear()
      list.forEach { matchResult ->
        val segments = matchResult.segments
        val problemSegment = segments.indexOfFirst {
          it.start >= checkStart && it.problem.isCritical
        }.takeIf { it >= 0 }

        val problemStart: Int
        val problemEnd: Int
        if (problemSegment != null) {
          problemStart = segments[problemSegment].start
          problemEnd = segments.asSequence()
            .filterIndexed { index, _ -> index >= problemSegment }
            .takeWhile { it.problem.isCritical }
            .last().end
        }
        else {
          problemStart = matchEnd + 1
          problemEnd = matchEnd + 1
        }

        if (problemStart > bestStart) {
          bestStart = problemStart
          bestEnd = matchEnd + 1
          result.clear()
        }
        if (problemStart == bestStart) {
          if (problemEnd < bestEnd) {
            bestEnd = problemEnd
            result.clear()
          }
          result.add(matchResult)
        }
      }
      if (bestStart <= checkStart) {
        checkStart++
      }
      else {
        checkStart = bestStart
      }

    }
    return result
  }

  private fun buildCompletionResultRuns(matchSegments: List<MatchResult>,
                                        params: CompletionParameters): List<CompletionResultRun> {
    val completionSegments = matchSegments
      .filter { it.start <= params.position && params.position <= it.end }

    fun Sequence<MatchResult>.buildPrevNames(): Set<String> =
      filter { match -> match.segments.none { it.problem.isCritical } }
        .map { params.name.substring(it.start, it.end) }
        .toSet()

    val result = SmartList<CompletionResultRun>()
    completionSegments.forEach { completionSegment ->
      result.add(CompletionResultRun(
        completionSegment.start, completionSegment.end,
        matchSegments.asSequence().takeWhile { it !== completionSegment }.buildPrevNames(),
        matchSegments
          .flatMap { it.segments.filter { segment -> segment.end <= completionSegment.start } },
      ))
      if (completionSegment.segments.none { it.problem.isCritical }
          && params.position == completionSegment.end
          && completionSegment === matchSegments.last()) {
        result.add(
          CompletionResultRun(
            completionSegment.end, completionSegment.end,
            matchSegments.asSequence().buildPrevNames(),
            matchSegments
              .flatMap { it.segments.filter { segment -> segment.end <= completionSegment.end } },
          )
        )
      }
    }
    return result
  }

  private data class CompletionResultRun(
    val start: Int,
    val end: Int,
    val prevNames: Set<String>,
    val prevMatchScope: List<WebSymbolNameSegment>,
  )

  override fun toString(): String =
    patterns.joinToString("\nor ")

}