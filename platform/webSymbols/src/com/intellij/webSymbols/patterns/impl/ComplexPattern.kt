// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider
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
                     itemsProvider: WebSymbolsPatternItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    process(scopeStack, params) { patterns, newItemsProvider, isDeprecated,
                                  isRequired, priority, proximity, repeats, unique ->
      performPatternMatch(params, start, end, patterns, repeats, unique, scopeStack, newItemsProvider)
        .let { matchResults ->
          if (!isRequired)
            matchResults.filter { matchResult ->
              matchResult.segments
                .all { it.problem != WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART }
            }
          else
            matchResults
        }
        // We need to filter and select match results separately for each length of the match
        .let { matchResults ->
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
          if (matchResults.isNotEmpty() && (isDeprecated == true || priority != null || proximity != null))
            matchResults.map { it.applyToSegments(deprecated = isDeprecated, priority = priority, proximity = proximity) }
          else matchResults
        }
        .let { matchResults ->
          if (!isRequired)
            matchResults + MatchResult(WebSymbolNameSegment(start, start))
          else if (matchResults.isEmpty())
            listOf(MatchResult(WebSymbolNameSegment(start, start, problem = WebSymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART)))
          else
            matchResults
        }
    }

  override fun getCompletionResults(owner: WebSymbol?,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    itemsProvider: WebSymbolsPatternItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    process(scopeStack, params) { patterns, newItemsProvider, isDeprecated,
                                  isRequired, priority, proximity, repeats, unique ->
      var staticPrefixes: Set<String> = emptySet()
      val runs = if (start == end) {
        listOf(Triple(start, start, emptySet()))
      }
      else if (repeats && getStaticPrefixes().filter { it != "" }.toSet().also { staticPrefixes = it }.isNotEmpty()) {
        repeatingPatternMatch(start, end, params, staticPrefixes, scopeStack, patterns, newItemsProvider, true)
          .map { (lastMatchStart, matchSegments) ->
            val prevNames = mutableSetOf<String>()
            for (matchedSegment in matchSegments) {
              if (matchedSegment.segments.none { it.problem.isCritical }) {
                prevNames.add(params.name.substring(matchedSegment.start, matchedSegment.end))
              }
            }
            matchSegments
              .find { matchSegment ->
                matchSegment.start <= params.position
                && (params.position < matchSegment.end
                    || (params.position == matchSegment.end
                        && matchSegment.segments.any { it.problem.isCritical }))
              }
              ?.let {
                prevNames.remove(params.name.substring(it.start, it.end))
                Triple(it.start, it.end, prevNames.toSet())
              }
            ?: if (matchSegments.lastOrNull()?.segments?.any { it.problem.isCritical } == true)
              Triple(lastMatchStart, end, prevNames.toSet())
            else
              Triple(end, end, prevNames.toSet())
          }
      }
      else {
        listOf(Triple(start, end, emptySet()))
      }

      val patternsItems = runs.flatMap { (localStart, localEnd, prevNames) ->
        patterns.flatMap { pattern ->
          pattern.getCompletionResults(null, scopeStack, newItemsProvider, params, localStart, localEnd)
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
              val defaultSource = itemsProvider?.delegate ?: scopeStack.peek() as? WebSymbol
              items.map { item ->
                item.with(
                  priority = priority ?: item.priority,
                  proximity = proximity?.let { (item.proximity ?: 0) + proximity } ?: item.proximity,
                  deprecated = isDeprecated == true || item.deprecated,
                  symbol = item.symbol ?: defaultSource,
                  completeAfterChars = (if (repeats) getStaticPrefixes().mapNotNull { it.getOrNull(0) }.toSet() else emptySet())
                                       + item.completeAfterChars
                )
              }
            }
        }
      }
      CompletionResults(patternsItems, isRequired)
    }

  private fun <T> process(scopeStack: Stack<WebSymbolsScope>,
                          params: MatchParameters,
                          action: (patterns: List<WebSymbolsPattern>,
                                   itemsProvider: WebSymbolsPatternItemsProvider?,
                                   patternDeprecated: Boolean?,
                                   patternRequired: Boolean,
                                   patternPriority: WebSymbol.Priority?,
                                   patternProximity: Int?,
                                   patternRepeat: Boolean,
                                   patternUnique: Boolean) -> T): T {
    val options = configProvider.getOptions(params, scopeStack)

    val delegate = options.delegate
    if (delegate != null) {
      scopeStack.push(delegate)
    }
    try {
      return action(patterns, options.itemsProvider, options.isDeprecated, options.isRequired, options.priority,
                    options.proximity, options.repeats, options.unique)
    }
    finally {
      if (delegate != null) {
        scopeStack.pop()
      }
    }
  }

  private fun performPatternMatch(params: MatchParameters,
                                  start: Int,
                                  end: Int,
                                  patterns: List<WebSymbolsPattern>,
                                  repeats: Boolean,
                                  unique: Boolean,
                                  contextStack: Stack<WebSymbolsScope>,
                                  newItemsProvider: WebSymbolsPatternItemsProvider?): List<MatchResult> {
    // shortcut
    if (start == end) {
      // This won't work for nested patterns, but at least allow for one level of empty
      // static pattern
      return patterns.asSequence()
        .filter { it is StaticPattern && it.content.isEmpty() }
        .flatMap {
          it.match(null, contextStack, newItemsProvider, params, start, end)
        }
        .toList()
    }
    val staticPrefixes: Set<String> = getStaticPrefixes().filter { it != "" }.toSet()
    return if (repeats && staticPrefixes.isNotEmpty()) {
      repeatingPatternMatch(start, end, params, staticPrefixes, contextStack, patterns, newItemsProvider, unique)
        .mapNotNull { it.second.reduceOrNull { a, b -> b.prefixedWith(a) } }
    }
    else {
      patterns.flatMap {
        it.match(null, contextStack, newItemsProvider, params, start, end)
      }
    }
  }

  private fun repeatingPatternMatch(start: Int,
                                    end: Int,
                                    params: MatchParameters,
                                    staticPrefixes: Set<String>,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    patterns: List<WebSymbolsPattern>,
                                    newItemsProvider: WebSymbolsPatternItemsProvider?,
                                    unique: Boolean): SmartList<Pair<Int, List<MatchResult>>> {
    val complete = SmartList<Pair<Int, List<MatchResult>>>()
    val toProcess = Stack<Pair<List<MatchResult>, List<CharSequence>>?>(null)
    while (toProcess.isNotEmpty()) {
      val prevResult = toProcess.pop()
      val matchStart = prevResult?.first?.lastOrNull()?.end ?: start
      val matchEnd = findMatchEnd(params.name, staticPrefixes, matchStart, end)

      withPrevMatchScope(scopeStack, prevResult?.first?.flatMap { it.segments }) {
        for (pattern in patterns) {
          pattern.match(null, scopeStack, newItemsProvider, params, matchStart, matchEnd).forEach {
            val prevMatchedSegments: List<CharSequence>
            var matchResult: MatchResult = it
            if (unique) {
              val cur = CharSequenceSubSequence(params.name, matchResult.start, matchResult.end)
              for (prev in prevResult?.second ?: emptyList()) {
                if (StringUtil.equals(prev, cur)) {
                  matchResult = MatchResult(
                    matchResult.segments.map { segment ->
                      if (segment.problem == null || segment.problem == WebSymbolNameSegment.MatchProblem.UNKNOWN_ITEM)
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

  private fun List<MatchResult>.selectShortestWithoutProblems(): List<MatchResult> {
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

  override fun toString(): String =
    patterns.joinToString("\nor ")

}