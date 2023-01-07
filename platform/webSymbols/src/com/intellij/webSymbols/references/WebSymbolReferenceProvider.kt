// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.inspections.WebSymbolsInspectionsPass.Companion.getDefaultProblemMessage
import com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP
import com.intellij.webSymbols.utils.asSingleSymbol
import com.intellij.webSymbols.utils.getProblemKind
import com.intellij.webSymbols.utils.hasOnlyExtensions
import java.util.*

private const val IJ_IGNORE_REFS = "ij-no-psi-refs"

abstract class WebSymbolReferenceProvider<T : PsiExternalReferenceHost> : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> =
    CachedValuesManager.getCachedValue(element, CachedValuesManager.getManager(element.project).getKeyForClass(this.javaClass)) {
      @Suppress("UNCHECKED_CAST")
      (CachedValueProvider.Result.create(getReferences(element as T),
                                         PsiModificationTracker.MODIFICATION_COUNT))
    }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()

  protected open fun getSymbol(psiElement: T): WebSymbol? = null

  protected open fun getSymbolNameOffset(psiElement: T): Int = 0

  protected open fun getOffsetsToSymbols(psiElement: T): Map<Int, WebSymbol> =
    getSymbol(psiElement)
      ?.let { mapOf(getSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  protected open fun shouldShowProblems(element: T): Boolean = true

  private fun getReferences(element: T): List<PsiSymbolReference> {
    val showProblems = shouldShowProblems(element)
    return getOffsetsToSymbols(element).flatMap { (offset, symbol) ->
      getReferences(element, offset, symbol, showProblems)
    }
  }

  private fun getReferences(element: T, symbolNameOffset: Int, symbol: WebSymbol, showProblems: Boolean): List<PsiSymbolReference> {
    fun WebSymbol.removeZeroLengthSegmentsRecursively(): List<WebSymbol> {
      val nameLength = matchedName.length
      return nameSegments
               .takeIf { it.size > 1 && it.none { segment -> segment.problem != null } }
               ?.find { segment -> segment.start == 0 && segment.end == nameLength }
               ?.let { segment -> segment.symbols.flatMap { it.removeZeroLengthSegmentsRecursively() } }
             ?: listOf(this)
    }

    val problemOnlyRanges = mutableMapOf<TextRange, Boolean>()
    val result = MultiMap<TextRange, WebSymbolNameSegment>()

    val queue = LinkedList(symbol.nameSegments.map { Pair(it, 0) })
    while (queue.isNotEmpty()) {
      val (nameSegment, offset) = queue.removeFirst()
      val symbols = nameSegment.symbols
      val range = TextRange(nameSegment.start + offset, nameSegment.end + offset)
      if (symbols.any { it.properties[IJ_IGNORE_REFS] == true }) continue
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
          .flatMap(WebSymbol::removeZeroLengthSegmentsRecursively)
          .takeWhile { it.nameSegments.size == 1 }

        if (unwrappedSymbols.isNotEmpty()) {
          result.putValue(range, WebSymbolNameSegment(0, nameSegment.end, unwrappedSymbols))
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
        val deprecation = segments.all { segment ->
          segment.deprecated
          || segment.symbols.let { it.isNotEmpty() && it.all { symbol -> symbol.deprecated } }
        }
        if (showProblems && (deprecation || problemOnly || segments.any { it.problem != null })) {
          NameSegmentReferenceWithProblem(element, range.shiftRight(symbolNameOffset), segments, deprecation, problemOnly)
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

  private open class NameSegmentReference(private val element: PsiElement,
                                          private val rangeInElement: TextRange,
                                          protected val nameSegments: Collection<WebSymbolNameSegment>)
    : WebSymbolReference {

    override fun getElement(): PsiElement = element

    override fun getRangeInElement(): TextRange =
      rangeInElement

    override fun resolveReference(): Collection<WebSymbol> =
      nameSegments
        .flatMap { it.symbols }
        .filter { !it.extension }
        .asSingleSymbol()
        ?.let { listOf(it) }
      ?: emptyList()

    override fun toString(): String {
      return "WebSymbolReference$rangeInElement - $nameSegments"
    }

  }

  private class NameSegmentReferenceWithProblem(element: PsiElement,
                                                rangeInElement: TextRange,
                                                nameSegments: Collection<WebSymbolNameSegment>,
                                                private val deprecation: Boolean,
                                                private val problemOnly: Boolean)
    : NameSegmentReference(element, rangeInElement, nameSegments) {

    override fun resolveReference(): Collection<WebSymbol> =
      if (problemOnly) emptyList() else super.resolveReference()

    override fun getProblems(): Collection<WebSymbolReferenceProblem> {
      val inspectionManager = InspectionManager.getInstance(element.project)
      val matchProblem = nameSegments
        .asSequence()
        .mapNotNull { segment ->
          val problemKind = segment.getProblemKind() ?: return@mapNotNull null
          val toolMapping = segment.symbolKinds.map {
            WebSymbolsInspectionToolMappingEP.get(it.namespace, it.kind, problemKind)
          }.firstOrNull()
          WebSymbolReferenceProblem(
            segment.symbolKinds,
            problemKind,
            inspectionManager.createProblemDescriptor(
              element, TextRange(segment.start, segment.end),
              toolMapping?.getProblemMessage(segment.displayName)
              ?: problemKind.getDefaultProblemMessage(segment.displayName),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true
            )
          )
        }.firstOrNull()
      val deprecationProblem = if (deprecation) {
        val symbolTypes = nameSegments.flatMapTo(LinkedHashSet()) { it.symbolKinds }
        val toolMapping = symbolTypes.map {
          WebSymbolsInspectionToolMappingEP.get(it.namespace, it.kind, WebSymbolReferenceProblem.ProblemKind.DeprecatedSymbol)
        }.firstOrNull()
        WebSymbolReferenceProblem(
          symbolTypes,
          WebSymbolReferenceProblem.ProblemKind.DeprecatedSymbol,
          inspectionManager.createProblemDescriptor(
            element, rangeInElement,
            toolMapping?.getProblemMessage(null)
            ?: WebSymbolReferenceProblem.ProblemKind.DeprecatedSymbol.getDefaultProblemMessage(null),
            ProblemHighlightType.LIKE_DEPRECATED, true
          )

        )
      }
      else null
      return listOfNotNull(matchProblem, deprecationProblem)
    }

  }

  companion object {

    @JvmStatic
    fun PsiElement.startOffsetIn(parent: PsiElement): Int {
      var result = 0
      var tmp: PsiElement? = this
      while (tmp != parent && tmp != null) {
        result += tmp.startOffsetInParent
        tmp = tmp.parent
      }
      return if (tmp != null)
        result
      else -1
    }
  }
}