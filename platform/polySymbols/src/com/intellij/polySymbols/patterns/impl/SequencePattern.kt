// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.impl.CompoundInsertHandler
import com.intellij.polySymbols.html.NAMESPACE_HTML
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.coalesceWith
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.polySymbols.utils.withOffset
import com.intellij.util.text.CharSequenceSubSequence

internal class SequencePattern(private val patternsProvider: () -> List<PolySymbolPattern>) : PolySymbolPattern() {

  constructor(vararg patterns: PolySymbolPattern) : this({ patterns.toList() })

  override fun getStaticPrefixes(): Sequence<String> {
    val patterns = patternsProvider()
    val firstStaticAndRequired = patterns
                                   .indexOfFirst { it.isStaticAndRequired() }
                                   .takeIf { it >= 0 } ?: (patterns.size - 1)
    return patterns.asSequence()
      .take(firstStaticAndRequired + 1)
      .flatMap { it.getStaticPrefixes() }
  }

  override fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters,
    start: Int,
    end: Int,
  ): List<MatchResult> =
    process(emptyList()) { matches, pattern, staticPrefixes ->
      if (matches.isEmpty()) {
        pattern.match(null, stack, symbolsResolver, params,
                      start,
                      findStaticStart(params, start, end, pattern, staticPrefixes))
      }
      else {
        matches.flatMap { prevResult ->
          withPrevMatchScope(stack, prevResult.segments) {
            pattern.match(null, stack, symbolsResolver, params,
                          prevResult.end,
                          findStaticStart(params, prevResult.end, end, pattern, staticPrefixes))
              .map { it.prefixedWith(prevResult) }
          }
        }
      }
    }

  override fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult> =
    process(emptyList()) { matches, pattern, _ ->
      if (matches.isEmpty()) {
        pattern.list(null, stack, symbolsResolver, params)
      }
      else {
        matches.flatMap { prevResult ->
          withPrevMatchScope(stack, prevResult.segments) {
            pattern.list(null, stack, symbolsResolver, params)
              .map { it.prefixedWith(prevResult) }
          }
        }
      }
    }

  override fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters,
    start: Int,
    end: Int,
  ): CompletionResults {
    val results: MutableList<PolySymbolCodeCompletionItem> = mutableListOf()
    var stop = false
    process(
      listOf(SequenceCompletionResult(
        MatchResult(PolySymbolNameSegment.create(start, start)),
        MatchResult(PolySymbolNameSegment.create(start, start)),
        null))
    ) { matches, pattern, staticPrefixes ->
      val completeAfterChars = staticPrefixes.mapNotNull { it.getOrNull(0) }.toList()
      matches.flatMap { (prevResult, lastMatched, requiredPart, onlyRequired) ->
        withPrevMatchScope(stack, requiredPart?.symbol?.nameSegments ?: lastMatched.segments) {
          val matchStart = lastMatched.end
          val matchEnd = findStaticStart(params, matchStart, end, pattern, staticPrefixes)
          val matchResults = if (prevResult == null) {
            listOf(null)
          }
          else {
            pattern.match(null, stack, symbolsResolver, params, matchStart, matchEnd)
              .map { it.prefixedWith(prevResult) }
              .ifEmpty { listOf(null) }
          }
          matchResults.flatMap inner@{ matchResult ->
            sliceRequiredPartIfNeeded(matchResult, pattern, matchStart, matchEnd, prevResult, symbolsResolver, params)
              ?.let {
                if (onlyRequired && requiredPart != null) {
                  results.add(requiredPart)
                }
                return@inner it
              }

            val completionResults = getCompletionResultsOnPattern(pattern, stack, symbolsResolver, matchResult, params, matchStart,
                                                                  prevResult)
            if (!completionResults.required && onlyRequired) {
              return@inner listOf(requiredPart.asRequiredOnlyCompletionResult(matchResult, params, lastMatched))
            }

            processCompletionResults(matchResult, pattern, matchStart, completionResults,
                                     params, requiredPart, end, stop, completeAfterChars,
                                     lastMatched, results)
              .also { stop = it.first }
              .second
          }
        }
      }
    }
      .forEach { it.requiredCompletionChain?.let(results::add) }
    return CompletionResults(results.map { it.withStopSequencePatternEvaluation(stop) }, true)
  }

  private fun PolySymbolCodeCompletionItem?.asRequiredOnlyCompletionResult(
    matchResult: MatchResult?,
    params: CompletionParameters,
    lastMatched: MatchResult,
  ) =
    SequenceCompletionResult(
      matchResult,
      matchResult?.takeIf { params.position >= it.end } ?: lastMatched,
      this, true
    )

  private fun sliceRequiredPartIfNeeded(
    matchResult: MatchResult?,
    pattern: PolySymbolPattern,
    matchStart: Int,
    matchEnd: Int,
    prevResult: MatchResult?,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters,
  ): List<SequenceCompletionResult>? =
    matchResult
      ?.takeIf {
        (matchResult.end < params.position)
        && (pattern is CompletionAutoPopupPattern
            || params.position !in matchStart..matchEnd
            || matchResult.end != (prevResult?.end ?: -1))
      }
      ?.let {
        if (pattern is CompletionAutoPopupPattern && !pattern.isSticky)
          if (symbolsResolver == null || matchResult.segments.any { it.problem == PolySymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART })
            emptyList()
          else
            listOf(SequenceCompletionResult(MatchResult(PolySymbolNameSegment.create(matchResult.end, matchResult.end)),
                                            MatchResult(PolySymbolNameSegment.create(matchResult.end, matchResult.end)),
                                            null))
        else
          listOf(SequenceCompletionResult(matchResult, matchResult, null))
      }

  private fun processCompletionResults(
    matchResult: MatchResult?,
    pattern: PolySymbolPattern,
    matchStart: Int,
    completionResults: CompletionResults,
    params: CompletionParameters,
    requiredPart: PolySymbolCodeCompletionItem?,
    end: Int,
    prevStop: Boolean,
    completeAfterChars: List<Char>,
    lastMatched: MatchResult,
    finalResults: MutableList<PolySymbolCodeCompletionItem>,
  ): Pair<Boolean, List<SequenceCompletionResult>> {
    val completionPos = params.position
    var requiredPartResult: List<PolySymbolCodeCompletionItem>? = requiredPart?.let { listOf(it) }
    val results = mutableListOf<SequenceCompletionResult>()

    fun PolySymbolCodeCompletionItem?.asRequiredOnlyCompletionResult() =
      this.asRequiredOnlyCompletionResult(matchResult, params, lastMatched)

    if (!completionResults.required) {
      if (requiredPart == null
          // Provide optional in-place completions only if we have a perfect match in preceding segments
          || (matchResult != null && matchResult.end == end
              && requiredPart.offset + requiredPart.name.length == matchStart
              && StringUtil.equals(requiredPart.name, CharSequenceSubSequence(params.name, matchResult.start, matchStart)))) {
        if (!prevStop) {
          results.addAll(
            completionResults.items.map { it.withCompleteAfterCharsAdded(completeAfterChars).asRequiredOnlyCompletionResult() })
        }
      }
      else {
        val withCompleteAfterInsert = completionResults.items.filter { it.completeAfterInsert }
        // Allow to complete the required part of sequence
        results.add(requiredPart.asRequiredOnlyCompletionResult())
        if (withCompleteAfterInsert.isNotEmpty()) {
          finalResults.addAll(explodeJoin(
            requiredPart,
            withCompleteAfterInsert.map { item ->
              (if (matchStart < item.offset)
                item.withPrefix(params.name.substring(matchStart, item.offset))
              else item)
                .withSymbol(null)
            },
            completeAfterChars
          ))
        }
        return Pair(prevStop, results)
      }
    }
    else {
      if (requiredPart == null && lastMatched.start == matchStart) {
        requiredPartResult = completionResults.items.map { item -> item.withCompleteAfterCharsAdded(completeAfterChars) }
      }
      else {
        val isMissingLastSegment = lastMatched.segments.lastOrNull()?.let { it.start == it.end && it.problem != null } == true
        val actualRequiredPart = if (requiredPart != null)
          requiredPart
        else {
          val name = params.name.substring(lastMatched.start, matchStart)
          PolySymbolCodeCompletionItem.create(
            name,
            offset = lastMatched.start,
            symbol = PolySymbolMatch.create(
              name,
              lastMatched.segments.filter { it.start < it.end }.withOffset(-lastMatched.start),
              PolySymbolQualifiedKind[NAMESPACE_HTML, SPECIAL_MATCHED_CONTRIB],
              PolySymbolOrigin.empty()
            ))
        }
        val result = mutableListOf<PolySymbolCodeCompletionItem>()
        val itemsToJoin = mutableListOf<PolySymbolCodeCompletionItem>()
        val hasCompleteAfterInsert = actualRequiredPart.completeAfterInsert
        var actualRequiredPartAdded = false
        completionResults.items.forEach { item ->
          if (hasCompleteAfterInsert && item.completeAfterInsert) {
            // If the required part already has completeAfterInsert do not concatenate it with another part, which also have completeAfterInsert
            if (!actualRequiredPartAdded) {
              result.add(actualRequiredPart)
              actualRequiredPartAdded = true
            }
          }
          // Avoid things like keyup..
          else if (isMissingLastSegment && item.name.isNotEmpty() && actualRequiredPart.name.endsWith(item.name))
            return@forEach
          else if (matchStart < item.offset)
            result.add(item)
          else
            itemsToJoin.add(item)
        }
        explodeJoin(actualRequiredPart, itemsToJoin, completeAfterChars)
          .forEach(result::add)
        requiredPartResult = result
      }
    }
    val stop = prevStop || completionResults.stop
               || matchResult?.let { it.start < completionPos && completionPos < it.end } == true
    if (pattern is CompletionAutoPopupPattern) {
      if (requiredPartResult != null) {
        finalResults.addAll(requiredPartResult)
      }
      if (matchResult != null
          && matchResult.end >= completionPos
          && matchResult.segments.all { it.problem != PolySymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART }) {
        if (pattern.isSticky)
          results.add(SequenceCompletionResult(matchResult, matchResult, requiredPart))
        else
          results.add(SequenceCompletionResult(
            MatchResult(PolySymbolNameSegment.create(matchResult.end, matchResult.end)),
            MatchResult(PolySymbolNameSegment.create(matchResult.end, matchResult.end)), null)
          )
      }
    }
    else {
      (requiredPartResult?.asSequence() ?: sequenceOf(null))
        .mapTo(results) { item ->
          SequenceCompletionResult(matchResult,
                                   matchResult?.takeIf { completionResults.required || completionPos >= it.end }
                                   ?: lastMatched,
                                   item)
        }
    }
    return Pair(stop, results)
  }

  private fun getCompletionResultsOnPattern(
    pattern: PolySymbolPattern,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    matchResult: MatchResult?,
    params: CompletionParameters,
    matchStart: Int,
    prevResult: MatchResult?,
  ) =
    pattern.complete(null, stack, symbolsResolver,
                     if (matchResult != null) params else params.withPosition(matchStart),
                     matchStart, matchResult?.end ?: matchStart)
      .let { completionResults ->
        val lastMatchedSegment = matchResult?.segments?.last()
        val matchedName = lastMatchedSegment?.let { CharSequenceSubSequence(params.name, it.start, it.end) }
        // Ensure that if we have a proper regex match,
        // we have that in completion items to correctly complete following segments
        if (lastMatchedSegment != null
            && lastMatchedSegment.problem.let { it == null || it == PolySymbolNameSegment.MatchProblem.DUPLICATE }
            && lastMatchedSegment.start <= params.position
            && matchResult.segments.size - 1 == (prevResult?.segments?.size ?: 0)
            && lastMatchedSegment.start < lastMatchedSegment.end
            && completionResults.items
              .none {
                val offset = lastMatchedSegment.start - it.offset
                offset >= 0 && it.name.length > offset
                && StringUtil.equals(CharSequenceSubSequence(it.name, offset, it.name.length), matchedName)
              }) {
          completionResults.copy(items = completionResults.items + PolySymbolCodeCompletionItem.builder(
            matchedName.toString(), lastMatchedSegment.start, lastMatchedSegment.symbols.asSingleSymbol())
            .completeAfterInsert(false)
            .build()
          )
        }
        else completionResults
      }

  private fun explodeJoin(
    requiredPart: PolySymbolCodeCompletionItem,
    newContributions: List<PolySymbolCodeCompletionItem>,
    completeAfterChar: List<Char>,
  ): List<PolySymbolCodeCompletionItem> =
    newContributions.map { new ->
      join(requiredPart, new, completeAfterChar)
    }

  private fun join(
    required: PolySymbolCodeCompletionItem, new: PolySymbolCodeCompletionItem,
    completeAfterChars: List<Char>,
  ): PolySymbolCodeCompletionItem {
    val displayName = if (required.completeAfterInsert || required.displayName != null || new.displayName != null)
      (required.displayName ?: required.name) + (new.displayName ?: new.name)
    else
      null

    val name = if (required.completeAfterInsert)
      required.name
    else
      required.name + new.name

    val symbol = concatSymbols(name, required.name.length, required.symbol, new.symbol)

    val proximity = if (required.proximity == null)
      new.proximity
    else if (new.proximity == null)
      required.proximity
    else
      maxOf(required.proximity!!, new.proximity!!)

    val priority = if (required.priority == null)
      new.priority
    else if (new.priority == null)
      required.priority
    else
      minOf(required.priority!!, new.priority!!)

    return if (required.completeAfterInsert)
      PolySymbolCodeCompletionItem
        .builder(name, required.offset, symbol)
        .displayName(displayName)
        .completeAfterInsert(true)
        .proximity(proximity)
        .priority(priority)
        .apiStatus(required.apiStatus.coalesceWith(new.apiStatus))
        .icon(new.icon ?: required.icon)
        .build()
    else
      PolySymbolCodeCompletionItem
        .builder(name, required.offset, symbol)
        .displayName(displayName)
        .completeAfterInsert(new.completeAfterInsert)
        .completeAfterChars(new.completeAfterChars + completeAfterChars)
        .proximity(proximity)
        .priority(priority)
        .apiStatus(required.apiStatus.coalesceWith(new.apiStatus))
        .icon(new.icon ?: required.icon)
        .insertHandler(CompoundInsertHandler.merge(required.insertHandler, new.insertHandler))
        .build()
  }

  private fun concatSymbols(name: String, firstNameLength: Int, first: PolySymbol?, second: PolySymbol?): PolySymbol? {

    fun PolySymbol?.toNameSegments(nameStart: Int, nameEnd: Int): List<PolySymbolNameSegment> =
      if (this == null)
        listOf(PolySymbolNameSegment.create(nameStart, nameEnd))
      else if (this is PolySymbolMatch && StringUtil.equals(matchedName, CharSequenceSubSequence(name, nameStart, nameEnd)))
        this.nameSegments.withOffset(nameStart)
      else
        listOf(PolySymbolNameSegment.create(nameStart, nameEnd, this))

    val mainSymbol = second ?: first ?: return null

    return PolySymbolMatch.create(name, mainSymbol.qualifiedKind, mainSymbol.origin) {
      addNameSegments(first.toNameSegments(0, firstNameLength))
      addNameSegments(second.toNameSegments(firstNameLength, name.length))
    }
  }

  private fun <T> process(
    initialMatches: List<T>,
    processor: (matches: List<T>, pattern: PolySymbolPattern, staticPrefixes: Set<String>) -> List<T>,
  ): List<T> {
    val list = patternsProvider()
    if (list.isEmpty()) return emptyList()

    fun getStaticPrefixes(index: Int): Set<String> =
      list.subList(index, list.size)
        .asSequence()
        .flatMap { it.getStaticPrefixes() }
        .plus("")
        .distinct()
        .toSet()

    var matches = initialMatches
    for (index in list.indices) {
      matches = processor(matches, list[index], getStaticPrefixes(index + 1))
      if (matches.isEmpty()) break
    }
    return matches
  }

  private fun findStaticStart(
    params: MatchParameters,
    start: Int,
    end: Int,
    pattern: PolySymbolPattern,
    staticPrefixes: Set<String>,
  ): Int {
    if (end == start) return start
    val toMatch = CharSequenceSubSequence(params.name, start, end)
    val patternPrefixes = pattern.getStaticPrefixes().filter { it != "" }.toSet()
    val searchStart = patternPrefixes
                        .maxOfOrNull { if (StringUtil.startsWith(toMatch, it)) it.length else 0 }
                        ?.takeIf { it > 0 } ?: 1
    return staticPrefixes
      .asSequence()
      .let {
        if (pattern is ComplexPattern)
          it.minus(patternPrefixes)
        else it
      }
      .minOf { if (it.isEmpty()) end else toMatch.indexOf(it, searchStart).let { ind -> if (ind < 0) end else start + ind } }
      .coerceAtLeast(start + searchStart)
  }

  override fun toString(): String =
    patternsProvider().toString()

  private data class SequenceCompletionResult(
    val prevResult: MatchResult?,
    val lastMatched: MatchResult,
    val requiredCompletionChain: PolySymbolCodeCompletionItem?,
    val onlyRequired: Boolean = false,
  )

}