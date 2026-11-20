// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ExpectedHighlightingData.ExpectedHighlightingSet
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.TestOnly

/**
 * Reads the highlighting markup of the test data without a document and without the IDE application.
 * An example of the markup is `<warning descr="Message">code</warning>`.
 *
 * [ExpectedHighlightingData] reads the same markup, but it needs the IDE application.
 * A host without it, for example the language server, uses this object.
 * [composeText] writes the markup of the actual highlighting with [ExpectedHighlightingData.composeText].
 *
 * The object reads the markers of the built-in severities and their `descr` attribute, as [ExpectedHighlightingData] does.
 * It matches the descriptions as [ExpectedHighlightingData] does when the match is not strict.
 * It ignores the other attributes, the severities of `SeveritiesProvider`, and the line markers.
 */
@TestOnly
object HighlightingMarkup {
  /** The description of a range without `descr`. It matches each description, as in [ExpectedHighlightingData]. */
  const val ANY_DESCRIPTION: String = "*"

  /** The text without the markup, and the highlighting of the markup. */
  class Parsed(val text: String, val highlights: List<HighlightInfo>)

  private class Marker(val severity: HighlightSeverity, val endOfLine: Boolean)

  // The markers of the ExpectedHighlightingData constructor.
  private val MARKERS = mapOf(
    CodeInsightTestFixture.ERROR_MARKER to Marker(HighlightSeverity.ERROR, false),
    CodeInsightTestFixture.WARNING_MARKER to Marker(HighlightSeverity.WARNING, false),
    CodeInsightTestFixture.WEAK_WARNING_MARKER to Marker(HighlightSeverity.WEAK_WARNING, false),
    "inject" to Marker(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false),
    "highlight" to Marker(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY, false),
    "injectedSyntax" to Marker(HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY, false),
    CodeInsightTestFixture.INFO_MARKER to Marker(HighlightSeverity.INFORMATION, false),
    CodeInsightTestFixture.TEXT_ATTRIBUTES_MARKER to Marker(HighlightSeverity.TEXT_ATTRIBUTES, false),
    "symbolName" to Marker(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false),
    CodeInsightTestFixture.END_LINE_HIGHLIGHT_MARKER to Marker(HighlightSeverity.ERROR, true),
    CodeInsightTestFixture.END_LINE_WARNING_MARKER to Marker(HighlightSeverity.WARNING, true),
  )

  // The attributes of a tag in ExpectedHighlightingData. Only `descr` has an effect here.
  private val TAG = Regex(
    """<(?<closing>/)?(?<marker>${MARKERS.keys.joinToString("|")})""" +
    """(?:\s+(?:descr="(?<descr>(?:\\"|[^"])*)"|\w+="(?:\\"|[^"])*"))*\s*(?<empty>/)?>"""
  )

  private val TYPE = HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, HighlighterColors.TEXT)

  /**
   * Removes the highlighting markup from [markedText] and returns the highlighting.
   * A range without `descr` gets [ANY_DESCRIPTION]. The value `descr="null"` gives no description.
   */
  fun parse(markedText: String): Parsed {
    val text = StringBuilder()
    val highlights = mutableListOf<HighlightInfo>()
    val open = ArrayDeque<Triple<String, Int, String?>>()
    var position = 0
    for (match in TAG.findAll(markedText)) {
      text.append(markedText, position, match.range.first)
      position = match.range.last + 1
      val marker = match.groups["marker"]!!.value
      when {
        match.groups["closing"] != null -> {
          val tag = "</$marker> at offset ${match.range.first} of the marked text"
          val (openMarker, start, description) = open.removeLastOrNull() ?: error("$tag has no start tag")
          require(openMarker == marker) { "$tag closes <$openMarker>" }
          highlights += create(marker, start, text.length, description)
        }
        match.groups["empty"] != null -> highlights += create(marker, text.length, text.length, description(match))
        else -> open.addLast(Triple(marker, text.length, description(match)))
      }
    }
    require(open.isEmpty()) { "No end tag for ${open.map { it.first }}" }
    text.append(markedText, position, markedText.length)
    return Parsed(text.toString(), highlights)
  }

  /**
   * Returns [actual] with the expected description on each range where the description of [expected] matches.
   * [ANY_DESCRIPTION] matches each description. A description that ends with an ellipsis matches each description with its prefix.
   * Then [composeText] gives the same text for the two lists when they match as in [ExpectedHighlightingData].
   */
  fun acceptMatchingDescriptions(expected: Collection<HighlightInfo>, actual: Collection<HighlightInfo>): List<HighlightInfo> {
    val expectedByRange = expected.groupBy { it.key() }
    return actual.map { info ->
      val match = expectedByRange[info.key()].orEmpty().firstOrNull { matches(it.description, info.description) }
      if (match == null || match.description == info.description) info
      else highlightInfo(info.severity, info.startOffset, info.endOffset, match.description, info.isAfterEndOfLine)
    }
  }

  private fun matches(expected: String?, actual: String?): Boolean {
    if (expected == ANY_DESCRIPTION) return true
    return expected != null && actual != null && expected.endsWith(StringUtil.ELLIPSIS) && actual.startsWith(expected.dropLast(1))
  }

  /** Inserts the markup of [highlights] into [text]. */
  fun composeText(text: String, highlights: Collection<HighlightInfo>): String {
    val types = MARKERS.mapValues { [_, marker] -> ExpectedHighlightingSet(marker.severity, marker.endOfLine, true) }
    return ExpectedHighlightingData.composeText(types, highlights, text)
  }

  /** Creates the highlighting of one range, as [parse] does. */
  fun highlightInfo(severity: HighlightSeverity, start: Int, end: Int, description: String?, endOfLine: Boolean = false): HighlightInfo {
    val builder = HighlightInfo.newHighlightInfo(TYPE).range(start, end).severity(severity)
    if (description != null) builder.description(description)
    if (endOfLine) builder.endOfLine()
    return builder.createUnconditionally()
  }

  private fun description(match: MatchResult): String? {
    val descr = match.groups["descr"]?.value ?: return ANY_DESCRIPTION
    if (descr == "null") return null
    return descr.replace("\\\\\"", "\"").replace("\\\"", "\"")
  }

  private fun create(marker: String, start: Int, end: Int, description: String?): HighlightInfo {
    val type = MARKERS.getValue(marker)
    return highlightInfo(type.severity, start, end, description, type.endOfLine)
  }

  private fun HighlightInfo.key() = listOf(severity, startOffset, endOffset, isAfterEndOfLine)
}
