// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.*
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.getMessage
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.polySymbols.highlighting.impl.getDefaultProblemMessage
import com.intellij.polySymbols.inspections.PolySymbolProblemQuickFixProvider
import com.intellij.polySymbols.inspections.impl.PolySymbolInspectionToolMappingEP
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolReferenceProviderListener
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.polySymbols.references.PolySymbolReferenceProblem
import com.intellij.polySymbols.references.PolySymbolReferenceProblem.ProblemKind
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import com.intellij.util.application
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import java.util.*

internal val IJ_IGNORE_REFS: PolySymbolProperty<Boolean> = PolySymbolProperty["ij-no-psi-refs"]

class PsiPolySymbolReferenceProviderImpl : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> =
    getSymbolOffsetsAndReferences(element, hints).second

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> =
    emptyList()

  internal fun getSymbolOffsetsAndReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Pair<MultiMap<Int, PolySymbol>, List<PolySymbolReference>> {
    val publisher = application.messageBus.syncPublisher(PolySymbolReferenceProviderListener.TOPIC)
    publisher.beforeProvideReferences(element, hints)

    try {
      val beans = PsiPolySymbolReferenceProviders.byLanguage(element.getLanguage()).byHostClass(element.javaClass)
      val result = SmartList<PolySymbolReference>()
      val offsets = MultiMap.createSet<Int, PolySymbol>()
      for (bean in beans) {
        @Suppress("UNCHECKED_CAST")
        val provider = bean.instance as PsiPolySymbolReferenceProvider<PsiExternalReferenceHost>
        val showProblems = provider.shouldShowProblems(element)
        val offsetsFromProvider = provider.getOffsetsToReferencedSymbols(element, hints)
        result.addAll(offsetsFromProvider.flatMap { (offset, symbol) ->
          getReferences(element, offset, symbol, showProblems)
        })
        offsets.putAllValues(offsetsFromProvider)
      }
      return Pair(offsets, result)
    } finally {
      publisher.afterProvideReferences(element, hints)
    }
  }

}

internal fun getReferences(element: PsiElement, symbolNameOffset: Int, symbol: PolySymbol, showProblems: Boolean): List<PolySymbolReference> {
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
        NameSegmentReferenceWithProblem(element, symbol, range.shiftRight(symbolNameOffset), segments, symbolNameOffset, deprecation, problemOnly)
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

private open class NameSegmentReference(
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

private class NameSegmentReferenceWithProblem(
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

  override fun getProblems(): Collection<PolySymbolReferenceProblem> {
    val inspectionManager = InspectionManager.getInstance(element.project)
    val matchProblem = nameSegments
      .asSequence()
      .mapNotNull { segment ->
        val problemKind = segment.getProblemKind() ?: return@mapNotNull null
        val toolMapping = segment.symbolKinds.map {
          PolySymbolInspectionToolMappingEP.get(it.namespace, it.kind, problemKind)
        }.firstOrNull()
        PolySymbolReferenceProblem.create(
          segment.symbolKinds,
          problemKind,
          inspectionManager.createProblemDescriptor(
            element, TextRange(segment.start + segmentsOffset, segment.end + segmentsOffset),
            toolMapping?.getProblemMessage(segment.displayName)
            ?: getDefaultProblemMessage(problemKind, segment.displayName),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
            *PolySymbolProblemQuickFixProvider.getQuickFixes(element, symbol, segment, problemKind).toTypedArray()
          )
        )
      }.firstOrNull()
    val deprecationProblem = if (apiStatus.isDeprecatedOrObsolete()) {
      val isDeprecated = apiStatus is PolySymbolApiStatus.Deprecated
      val symbolTypes = nameSegments.flatMapTo(LinkedHashSet()) { it.symbolKinds }
      val toolMapping = symbolTypes.map {
        if (apiStatus is PolySymbolApiStatus.Obsolete)
          PolySymbolInspectionToolMappingEP.get(it.namespace, it.kind, ProblemKind.ObsoleteSymbol)
            ?.let { mapping -> return@map mapping }
        PolySymbolInspectionToolMappingEP.get(it.namespace, it.kind, ProblemKind.DeprecatedSymbol)
      }.firstOrNull()

      val cause = apiStatus?.getMessage()
                    ?.takeIf { it.isNotBlank() }
                    ?.sanitizeHtmlOutputForProblemMessage()
                  ?: PolySymbolsBundle.message("web.inspection.message.deprecated.symbol.explanation")

      @Suppress("HardCodedStringLiteral")
      val prefix = toolMapping
                     ?.getProblemMessage(null)
                     ?.trim()
                     ?.removeSuffix(".")
                     ?.let { if (!it.endsWith(",")) "$it," else it }
                   ?: apiStatus?.since
                     ?.let {
                       PolySymbolsBundle.message(if (isDeprecated) "web.inspection.message.deprecated.symbol.since"
                                                 else "web.inspection.message.obsolete.symbol.since", it)
                     }
                   ?: PolySymbolsBundle.message(if (isDeprecated) "web.inspection.message.deprecated.symbol.message"
                                                else "web.inspection.message.obsolete.symbol.message")

      PolySymbolReferenceProblem.create(
        symbolTypes,
        if (isDeprecated) ProblemKind.DeprecatedSymbol else ProblemKind.ObsoleteSymbol,
        inspectionManager.createProblemDescriptor(
          element, rangeInElement,
          "$prefix ${StringUtil.decapitalize(cause)}",
          if (isDeprecated) ProblemHighlightType.LIKE_DEPRECATED else ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL,
          true
        )

      )
    }
    else null
    return listOfNotNull(matchProblem, deprecationProblem)
  }

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

@Suppress("HardCodedStringLiteral")
private fun @Nls String.sanitizeHtmlOutputForProblemMessage(): @Nls String =
  this.replace(Regex("</?code>"), "`")
    .replace(Regex("</?[a-zA-Z-]+[^>]*>"), "")
    .let {
      StringUtil.unescapeXmlEntities(it)
    }

private fun PolySymbolNameSegment.getProblemKind(): ProblemKind? =
  when (problem) {
    PolySymbolNameSegment.MatchProblem.MISSING_REQUIRED_PART -> ProblemKind.MissingRequiredPart
    PolySymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL ->
      if (start == end)
        ProblemKind.MissingRequiredPart
      else
        ProblemKind.UnknownSymbol
    PolySymbolNameSegment.MatchProblem.DUPLICATE -> ProblemKind.DuplicatedPart
    null -> null
  }