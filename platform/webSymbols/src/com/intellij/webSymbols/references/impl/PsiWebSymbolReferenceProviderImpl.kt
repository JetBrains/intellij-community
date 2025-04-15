// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references.impl

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbolApiStatus.Companion.getMessage
import com.intellij.webSymbols.WebSymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.webSymbols.highlighting.impl.getDefaultProblemMessage
import com.intellij.webSymbols.impl.removeZeroLengthSegmentsRecursively
import com.intellij.webSymbols.inspections.WebSymbolsProblemQuickFixProvider
import com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP
import com.intellij.webSymbols.references.PsiWebSymbolReferenceProvider
import com.intellij.webSymbols.references.WebSymbolReference
import com.intellij.webSymbols.references.WebSymbolReferenceProblem
import com.intellij.webSymbols.references.WebSymbolReferenceProblem.ProblemKind
import com.intellij.webSymbols.utils.*
import org.jetbrains.annotations.Nls
import java.util.*

internal const val IJ_IGNORE_REFS = "ij-no-psi-refs"

class PsiWebSymbolReferenceProviderImpl : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> =
    getSymbolOffsetsAndReferences(element, hints).second

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> =
    emptyList()

  internal fun getSymbolOffsetsAndReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Pair<MultiMap<Int, WebSymbol>, List<WebSymbolReference>> =
    CachedValuesManager.getCachedValue(element, CachedValuesManager.getManager(element.project).getKeyForClass(this.javaClass)) {
      val beans = PsiWebSymbolReferenceProviders.byLanguage(element.getLanguage()).byHostClass(element.javaClass)
      val result = SmartList<WebSymbolReference>()
      val offsets = MultiMap.createSet<Int, WebSymbol>()
      for (bean in beans) {
        @Suppress("UNCHECKED_CAST")
        val provider = bean.instance as PsiWebSymbolReferenceProvider<PsiExternalReferenceHost>
        val showProblems = provider.shouldShowProblems(element)
        val offsetsFromProvider = provider.getOffsetsToReferencedSymbols(element, hints)
        result.addAll(offsetsFromProvider.flatMap { (offset, symbol) ->
          getReferences(element, offset, symbol, showProblems)
        })
        offsets.putAllValues(offsetsFromProvider)
      }
      CachedValueProvider.Result.create(Pair(offsets, result), element.containingFile, PsiModificationTracker.MODIFICATION_COUNT)
    }

}

internal fun getReferences(element: PsiElement, symbolNameOffset: Int, symbol: WebSymbol, showProblems: Boolean): List<WebSymbolReference> {
  val problemOnlyRanges = mutableMapOf<TextRange, Boolean>()
  val result = MultiMap<TextRange, WebSymbolNameSegment>()

  val queue = LinkedList(symbol.nameSegments.map { Pair(it, 0) })
  while (queue.isNotEmpty()) {
    val (nameSegment, offset) = queue.removeFirst()
    val symbols = nameSegment.symbols
    val range = TextRange(nameSegment.start + offset, nameSegment.end + offset)
    if (symbols.any { it.properties[IJ_IGNORE_REFS] == true })
      continue
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
        result.putValue(range, WebSymbolNameSegment.create(0, nameSegment.end, unwrappedSymbols))
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

private fun isSymbolDeclaration(symbol: WebSymbol, element: PsiElement, range: TextRange): Boolean =
  symbol is WebSymbolDeclaredInPsi && symbol.sourceElement == element && symbol.textRangeInSourceElement == range

private open class NameSegmentReference(
  private val element: PsiElement,
  private val rangeInElement: TextRange,
  val nameSegments: Collection<WebSymbolNameSegment>,
) : WebSymbolReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange =
    rangeInElement

  override fun resolveReference(): Collection<WebSymbol> =
    nameSegments
      .flatMap { it.symbols }
      .filter { !it.extension }
      .map {
        if (isSymbolDeclaration(it, element, rangeInElement))
          if (it is PsiSourcedWebSymbol)
            NoNavigationTargetPsiSourcedWebSymbolDeclaredInPsiDelegate(it)
          else
            NoNavigationTargetWebSymbolDeclaredInPsiDelegate(it as WebSymbolDeclaredInPsi)
        else
          it
      }
      .asSingleSymbol(force = true)
      ?.let { listOf(it) }
    ?: emptyList()

  override fun toString(): String {
    return "WebSymbolReference$rangeInElement - $nameSegments"
  }

}

private class NameSegmentReferenceWithProblem(
  element: PsiElement,
  private val symbol: WebSymbol,
  rangeInElement: TextRange,
  nameSegments: Collection<WebSymbolNameSegment>,
  private val segmentsOffset: Int,
  private val apiStatus: WebSymbolApiStatus?,
  private val problemOnly: Boolean,
) : NameSegmentReference(element, rangeInElement, nameSegments) {

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
        WebSymbolReferenceProblem.create(
          segment.symbolKinds,
          problemKind,
          inspectionManager.createProblemDescriptor(
            element, TextRange(segment.start + segmentsOffset, segment.end + segmentsOffset),
            toolMapping?.getProblemMessage(segment.displayName)
            ?: getDefaultProblemMessage(problemKind, segment.displayName),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
            *WebSymbolsProblemQuickFixProvider.getQuickFixes(element, symbol, segment, problemKind).toTypedArray()
          )
        )
      }.firstOrNull()
    val deprecationProblem = if (apiStatus.isDeprecatedOrObsolete()) {
      val isDeprecated = apiStatus is WebSymbolApiStatus.Deprecated
      val symbolTypes = nameSegments.flatMapTo(LinkedHashSet()) { it.symbolKinds }
      val toolMapping = symbolTypes.map {
        if (apiStatus is WebSymbolApiStatus.Obsolete)
          WebSymbolsInspectionToolMappingEP.get(it.namespace, it.kind, ProblemKind.ObsoleteSymbol)
            ?.let { mapping -> return@map mapping }
        WebSymbolsInspectionToolMappingEP.get(it.namespace, it.kind, ProblemKind.DeprecatedSymbol)
      }.firstOrNull()

      val cause = apiStatus?.getMessage()
                    ?.takeIf { it.isNotBlank() }
                    ?.sanitizeHtmlOutputForProblemMessage()
                  ?: WebSymbolsBundle.message("web.inspection.message.deprecated.symbol.explanation")

      @Suppress("HardCodedStringLiteral")
      val prefix = toolMapping
                     ?.getProblemMessage(null)
                     ?.trim()
                     ?.removeSuffix(".")
                     ?.let { if (!it.endsWith(",")) "$it," else it }
                   ?: apiStatus?.since
                     ?.let {
                       WebSymbolsBundle.message(if (isDeprecated) "web.inspection.message.deprecated.symbol.since"
                                                else "web.inspection.message.obsolete.symbol.since", it)
                     }
                   ?: WebSymbolsBundle.message(if (isDeprecated) "web.inspection.message.deprecated.symbol.message"
                                               else "web.inspection.message.obsolete.symbol.message")

      WebSymbolReferenceProblem.create(
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

@Suppress("HardCodedStringLiteral")
private fun @Nls String.sanitizeHtmlOutputForProblemMessage(): @Nls String =
  this.replace(Regex("</?code>"), "`")
    .replace(Regex("</?[a-zA-Z-]+[^>]*>"), "")
    .let {
      StringUtil.unescapeXmlEntities(it)
    }

/**
 * Special delegate class to disable navigation. This is used only in case the referenced symbol is present in its declaration location.
 * Platform prioritizes references over declarations, so to enable the find usages action on CMD+Click, we need to disable navigation
 * for the symbol.
 */
private open class NoNavigationTargetWebSymbolDeclaredInPsiDelegate(delegate: WebSymbolDeclaredInPsi) : WebSymbolDelegate<WebSymbolDeclaredInPsi>(delegate), WebSymbolDeclaredInPsi {

  override val psiContext: PsiElement?
    get() = delegate.psiContext

  override val sourceElement: PsiElement?
    get() = delegate.sourceElement

  override val textRangeInSourceElement: TextRange?
    get() = delegate.textRangeInSourceElement

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = emptyList()

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol || delegate.isEquivalentTo(symbol)

  override fun equals(other: Any?): Boolean =
    other === this ||
    (other is NoNavigationTargetWebSymbolDeclaredInPsiDelegate && other.delegate == delegate) ||
    other == delegate

  override fun hashCode(): Int =
    delegate.hashCode()

  override fun createPointer(): Pointer<out NoNavigationTargetWebSymbolDeclaredInPsiDelegate> {
    val delegatePtr = delegate.createPointer()
    return Pointer {
      delegatePtr.dereference()?.let { NoNavigationTargetWebSymbolDeclaredInPsiDelegate(it) }
    }
  }
}

private class NoNavigationTargetPsiSourcedWebSymbolDeclaredInPsiDelegate(delegate: PsiSourcedWebSymbol) : NoNavigationTargetWebSymbolDeclaredInPsiDelegate(delegate as WebSymbolDeclaredInPsi), PsiSourcedWebSymbol {

  override val source: PsiElement?
    get() = (delegate as PsiSourcedWebSymbol).source

  override val psiContext: PsiElement?
    get() = super<PsiSourcedWebSymbol>.psiContext

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol || delegate.isEquivalentTo(symbol)

  override fun createPointer(): Pointer<NoNavigationTargetPsiSourcedWebSymbolDeclaredInPsiDelegate> {
    val delegatePtr = delegate.createPointer()
    return Pointer {
      delegatePtr.dereference()?.let { NoNavigationTargetPsiSourcedWebSymbolDeclaredInPsiDelegate(it as PsiSourcedWebSymbol) }
    }
  }
}