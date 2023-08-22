// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style.string

import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind.*
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil

private val QUOTE_TO_KIND: Map<String, InspectionStringQuotationKind> = listOf(
  GrStringUtil.QUOTE to SINGLE_QUOTED,
  GrStringUtil.DOUBLE_QUOTES to DOUBLE_QUOTED,
  GrStringUtil.TRIPLE_QUOTES to TRIPLE_QUOTED,
  GrStringUtil.TRIPLE_DOUBLE_QUOTES to TRIPLE_DOUBLE_QUOTED,
  GrStringUtil.SLASH to SLASHY,
  GrStringUtil.DOLLAR_SLASH to DOLLAR_SLASHY_QUOTED).toMap()


/**
 * @return Best quotation kind (the one that minimizes escaping) for the text
 * and amount of characters that will be unescaped in comparison with current literal text.
 * Returns null if there is no better quotation.
 */
internal fun findBestQuotationForEscaping(literalText: String,
                                          fallbackKindForEscapedStrings: InspectionStringQuotationKind,
                                          mainStringKind: InspectionStringQuotationKind): Pair<InspectionStringQuotationKind, Int>? {
  val startQuote = GrStringUtil.getStartQuote(literalText)
  val unescapedText = GrStringUtil.unescapeString(GrStringUtil.removeQuotes(literalText))

  val currentQuotation = QUOTE_TO_KIND[startQuote] ?: return null
  val scores = countScores(unescapedText)
  val currentScore = scores[currentQuotation] ?: return null
  val bestEscaping = scores
    .minWithOrNull { (leftKind, leftScore), (rightKind, rightScore) ->
      if (leftScore == rightScore) {
        when {
          leftKind == mainStringKind -> -1
          rightKind == mainStringKind -> 1
          leftKind == currentQuotation -> -1
          rightKind == currentQuotation -> 1
          leftKind == fallbackKindForEscapedStrings -> -1
          rightKind == fallbackKindForEscapedStrings -> 1
          else -> leftKind.toString().compareTo(rightKind.toString()) // induce deterministic total order
        }
      }
      else {
        leftScore.compareTo(rightScore)
      }
    }
  return bestEscaping?.let { it.key to currentScore - it.value }?.takeIf { it.second >= 0 }
}


private fun countScores(text: String): Map<InspectionStringQuotationKind, Int> {
  var doubleQuotes = 0
  var singleQuotes = 0
  var dollars = 0
  var doubleQuoteSequence = 0
  var tripleQuotes = 0
  var tripleDoubleQuotes = 0
  var singleQuoteSequence = 0
  var slashes = 0
  var reversedSlashes = 0
  for (c in text) {
    if (c == '"') {
      doubleQuotes += 1
      doubleQuoteSequence += 1
    } else {
      tripleDoubleQuotes += doubleQuoteSequence / 3
      doubleQuoteSequence = 0
    }
    if (c == '\'') {
      singleQuotes += 1
      singleQuoteSequence += 1
    } else {
      tripleQuotes += singleQuoteSequence / 3
      singleQuoteSequence = 0
    }
    when (c) {
      '$' -> dollars += 1
      '/' -> slashes += 1
      '\\' -> reversedSlashes += 1
    }
  }
  return mapOf(DOUBLE_QUOTED to doubleQuotes + reversedSlashes + dollars,
               SINGLE_QUOTED to singleQuotes + reversedSlashes,
               TRIPLE_QUOTED to tripleQuotes * 3 + reversedSlashes,
               TRIPLE_DOUBLE_QUOTED to tripleDoubleQuotes * 3 + reversedSlashes + dollars,
               DOLLAR_SLASHY_QUOTED to dollars,
               SLASHY to slashes)
}
