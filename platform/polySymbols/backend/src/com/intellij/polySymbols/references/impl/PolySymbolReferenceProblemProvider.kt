// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.getMessage
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.highlighting.impl.getDefaultProblemMessage
import com.intellij.polySymbols.inspections.PolySymbolProblemQuickFixProvider
import com.intellij.polySymbols.inspections.impl.PolySymbolInspectionToolMappingEP
import com.intellij.polySymbols.references.PolySymbolReferenceProblem
import com.intellij.polySymbols.references.PolySymbolReferenceProblem.ProblemKind
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

internal object PolySymbolReferenceProblemProvider {

  fun getProblems(
    element: PsiElement,
    symbol: PolySymbol,
    rangeInElement: TextRange,
    nameSegments: Collection<PolySymbolNameSegment>,
    segmentsOffset: Int,
    apiStatus: PolySymbolApiStatus?,
  ): Collection<PolySymbolReferenceProblem> {
    val inspectionManager = InspectionManager.getInstance(element.project)
    val matchProblem = nameSegments
      .asSequence()
      .mapNotNull { segment ->
        val problemKind = segment.getProblemKind() ?: return@mapNotNull null
        val toolMapping = segment.symbolKinds.map {
          PolySymbolInspectionToolMappingEP.get(it.namespace, it.kindName, problemKind)
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
          PolySymbolInspectionToolMappingEP.get(it.namespace, it.kindName, ProblemKind.ObsoleteSymbol)
            ?.let { mapping -> return@map mapping }
        PolySymbolInspectionToolMappingEP.get(it.namespace, it.kindName, ProblemKind.DeprecatedSymbol)
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


}
