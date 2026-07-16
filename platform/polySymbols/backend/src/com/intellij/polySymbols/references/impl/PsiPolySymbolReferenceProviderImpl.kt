// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.polySymbols.references.PolySymbolReferenceProblem
import com.intellij.polySymbols.references.PsiPolySymbolReferenceCacheInfoProvider
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProviderListener
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.application
import com.intellij.util.containers.MultiMap
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

internal val IJ_IGNORE_REFS: PolySymbolProperty<Boolean> = PolySymbolProperty["ij-no-psi-refs"]

class PsiPolySymbolReferenceProviderImpl : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> =
    getSymbolOffsetsAndReferences(element, hints).second

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> =
    emptyList()

  internal fun getSymbolOffsetsAndReferences(
    element: PsiExternalReferenceHost,
    hints: PsiSymbolReferenceHints,
  ): Pair<MultiMap<Int, PolySymbol>, List<PolySymbolReference>> {
    val target = hints.target
    if (target != null && getProviders(element).none { it.canReference(target) })
      return Pair(MultiMap.create(), emptyList())
    val cacheKeys =
      PsiPolySymbolReferenceCacheInfoProvider.getCacheKeys(element, target)

    val cache = CachedValuesManager.getCachedValue(element) {
      CachedValueProvider.Result.create(ConcurrentHashMap<Any, Pair<MultiMap<Int, PolySymbol>, List<PolySymbolReference>>>(),
                                        element.containingFile, PsiModificationTracker.MODIFICATION_COUNT)
    }

    // Do not calculate references within computeIfAbsent, as it can lead to a blocking call in a non-IO context in coroutines,
    // causing a coroutine to fail cancellation.
    return cache[cacheKeys].let {
      it ?: getSymbolOffsetsAndReferencesNoCache(element, target).also { result ->
        cache.putIfAbsent(cacheKeys, result)
      }
    }
  }

  private fun getSymbolOffsetsAndReferencesNoCache(
    element: PsiExternalReferenceHost,
    targetSymbol: Symbol?,
  ): Pair<MultiMap<Int, PolySymbol>, List<PolySymbolReference>> {
    val publisher = application.messageBus.syncPublisher(PsiPolySymbolReferenceProviderListener.TOPIC)
    publisher.beforeProvideReferences(element, targetSymbol)

    try {
      val result = SmartList<PolySymbolReference>()
      val offsets = MultiMap.createSet<Int, PolySymbol>()
      for (provider in getProviders(element)) {
        val showProblems = provider.shouldShowProblems(element)
        val offsetsFromProvider = provider.getOffsetsToReferencedSymbols(element)
        result.addAll(createPolySymbolReferences(element, offsetsFromProvider, showProblems))
        offsets.putAllValues(offsetsFromProvider)
      }
      return Pair(offsets, result)
    }
    finally {
      publisher.afterProvideReferences(element, targetSymbol)
    }
  }

  private fun getProviders(element: PsiExternalReferenceHost): Sequence<PsiPolySymbolReferenceProvider<PsiExternalReferenceHost>> =
    @Suppress("UNCHECKED_CAST")
    PsiPolySymbolReferenceProviders.byLanguage(element.getLanguage()).byHostClass(element.javaClass)
      .asSequence().map { it.instance as PsiPolySymbolReferenceProvider<PsiExternalReferenceHost> }

}

/**
 * The implementation details of how [PolySymbol] offsets are expanded into [PolySymbolReference]s stay in
 * this `impl` package. [com.intellij.polySymbols.references.PolySymbolOwnReferences] (the own-references
 * builder) calls into this function too, so both the EP-based path above and own references produce
 * identically-shaped references.
 */
internal fun createPolySymbolReferences(
  element: PsiElement,
  offsetsToSymbols: Map<Int, PolySymbol>,
  showProblems: Boolean,
): List<PolySymbolReference> =
  offsetsToSymbols.flatMap { (offset, symbol) -> createPolySymbolReferences(element, offset, symbol, showProblems) }

internal fun createPolySymbolReferences(
  element: PsiElement,
  symbolNameOffset: Int,
  symbol: PolySymbol,
  showProblems: Boolean,
): List<PolySymbolReference> {
  val problemOnlyRanges = mutableMapOf<TextRange, Boolean>()
  val result = MultiMap<TextRange, PolySymbolNameSegment>()

  val queue = LinkedList(symbol.nameSegments.map { Pair(it, 0) })
  while (queue.isNotEmpty()) {
    val (nameSegment, offset) = queue.removeFirst()
    val symbols = nameSegment.symbols
    val range = TextRange(nameSegment.start + offset, nameSegment.end + offset)
    if (symbols.any { it[IJ_IGNORE_REFS] == true }) continue
    if (symbols.all { it.nameSegments.size == 1 }) {
      if (nameSegment.problem != null || symbols.let { it.isNotEmpty() && !it.hasOnlyExtensions() }) {
        result.putValue(range, nameSegment)
        problemOnlyRanges[range] = false
      }
    }
    else {
      if (nameSegment.problem != null) {
        result.putValue(range, nameSegment)
        problemOnlyRanges.putIfAbsent(range, true)
      }
      val unwrappedSymbols = symbols
        .flatMap(PolySymbol::removeZeroLengthSegmentsRecursively)
        .takeWhile { it.nameSegments.size == 1 }

      if (unwrappedSymbols.isNotEmpty()) {
        result.putValue(range, PolySymbolNameSegment.create(0, nameSegment.end, unwrappedSymbols))
        problemOnlyRanges[range] = false
      }
      else {
        symbols.getOrNull(0)
          ?.let { s ->
            queue.addAll(s.nameSegments.map { Pair(it, offset + nameSegment.start) })
          }
      }
    }
  }

  return result.entrySet()
    .asSequence()
    .mapNotNull { (range, segments) ->
      val problemOnly = problemOnlyRanges[range] ?: false
      val deprecation = segments.mapNotNull mapSegments@{ segment ->
        segment.apiStatus.takeIf { it.isDeprecatedOrObsolete() }
          ?.let { return@mapSegments it }
        val declarations = segment.symbols.filter { !it.extension }
        declarations
          .mapNotNull { decl -> decl.apiStatus.takeIf { it.isDeprecatedOrObsolete() } }
          .takeIf { it.size == declarations.size }
          ?.firstOrNull()
      }.takeIf { it.size == segments.size }?.firstOrNull()
      if (showProblems && (deprecation != null || problemOnly || segments.any { it.problem != null })) {
        NameSegmentReferenceWithProblem(element, symbol, range.shiftRight(symbolNameOffset),
                                        segments, symbolNameOffset, deprecation, problemOnly)
      }
      else if (!range.isEmpty && !problemOnly) {
        NameSegmentReference(element, range.shiftRight(symbolNameOffset), segments)
      }
      else null
    }
    .sortedWith(Comparator.comparingInt<PsiSymbolReference> { it.rangeInElement.startOffset }
                  .thenComparingInt { it.rangeInElement.length }
    )
    .toList()
}

internal open class NameSegmentReference(
  private val element: PsiElement,
  private val rangeInElement: TextRange,
  val nameSegments: Collection<PolySymbolNameSegment>,
) : PolySymbolReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange =
    rangeInElement

  override fun resolveReference(): Collection<PolySymbol> =
    nameSegments
      .flatMap { it.symbols }
      .filter { !it.extension }
      .asSingleSymbol(force = true)
      ?.let { listOf(it) }
    ?: emptyList()

  override fun toString(): String {
    return "PolySymbolReference$rangeInElement - $nameSegments"
  }

}

internal class NameSegmentReferenceWithProblem(
  element: PsiElement,
  private val symbol: PolySymbol,
  rangeInElement: TextRange,
  nameSegments: Collection<PolySymbolNameSegment>,
  private val segmentsOffset: Int,
  private val apiStatus: PolySymbolApiStatus?,
  private val problemOnly: Boolean,
) : NameSegmentReference(element, rangeInElement, nameSegments) {

  override fun resolveReference(): Collection<PolySymbol> =
    if (problemOnly) emptyList() else super.resolveReference()

  override fun getProblems(): Collection<PolySymbolReferenceProblem> =
    PolySymbolReferenceProblemProvider.getProblems(
      element, symbol, rangeInElement, nameSegments, segmentsOffset, apiStatus
    )

}

private fun PolySymbol.removeZeroLengthSegmentsRecursively(): List<PolySymbol> {
  if (this !is PolySymbolMatch) return listOf(this)
  val nameLength = matchedName.length
  return nameSegments
           .takeIf { it.size > 1 && it.none { segment -> segment.problem != null } }
           ?.find { segment -> segment.start == 0 && segment.end == nameLength }
           ?.let { segment -> segment.symbols.flatMap { it.removeZeroLengthSegmentsRecursively() } }
         ?: listOf(this)
}
