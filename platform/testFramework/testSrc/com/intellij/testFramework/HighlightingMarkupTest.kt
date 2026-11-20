// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.HighlightingMarkup.ANY_DESCRIPTION
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HighlightingMarkupTest {
  @Test
  fun parseRemovesTheMarkupAndGivesTheRanges() {
    val parsed = HighlightingMarkup.parse("""class X { <warning descr="Use \"equals()\"">a == b</warning>;<EOLError descr="null"></EOLError> }""")
    assertEquals("class X { a == b; }", parsed.text)
    assertEquals(listOf(listOf(HighlightSeverity.WARNING, 10, 16, "Use \"equals()\"", false),
                        listOf(HighlightSeverity.ERROR, 17, 17, null, true)),
                 parsed.highlights.map { listOf(it.severity, it.startOffset, it.endOffset, it.description, it.isAfterEndOfLine) })
  }

  @Test
  fun composeTextGivesTheMarkupThatParseReads() {
    val marked = """0<warning descr="A">12<weak_warning descr="B">34</weak_warning>567</warning>89"""
    val parsed = HighlightingMarkup.parse(marked)
    assertEquals(marked, HighlightingMarkup.composeText(parsed.text, parsed.highlights))
  }

  @Test
  fun aRangeWithoutDescriptionAcceptsAnyDescription() {
    val parsed = HighlightingMarkup.parse("""int <warning>x</warning>, <warning descr="B">y</warning>;""")
    val reported = listOf(HighlightingMarkup.highlightInfo(HighlightSeverity.WARNING, 4, 5, "A"),
                          HighlightingMarkup.highlightInfo(HighlightSeverity.WARNING, 7, 8, "C"))
    val actual = HighlightingMarkup.acceptMatchingDescriptions(parsed.highlights, reported)
    assertEquals(listOf(ANY_DESCRIPTION, "C"), actual.map { it.description })
  }

  @Test
  fun aDescriptionWithEllipsisAcceptsEachDescriptionWithThePrefix() {
    val parsed = HighlightingMarkup.parse("""int <warning descr="Call to 'foo()' may…">x</warning>, <warning descr="Other…">y</warning>;""")
    val reported = listOf(HighlightingMarkup.highlightInfo(HighlightSeverity.WARNING, 4, 5, "Call to 'foo()' may fail"),
                          HighlightingMarkup.highlightInfo(HighlightSeverity.WARNING, 7, 8, "Another message"))
    val actual = HighlightingMarkup.acceptMatchingDescriptions(parsed.highlights, reported)
    assertEquals(listOf("Call to 'foo()' may…", "Another message"), actual.map { it.description })
  }

  @Test
  fun anEndTagWithoutStartTagGivesTheOffsetOfTheTag() {
    val e = assertThrows(IllegalStateException::class.java) { HighlightingMarkup.parse("ab</warning>") }
    assertEquals("</warning> at offset 2 of the marked text has no start tag", e.message)
  }

  @Test
  fun anEndTagOfAnotherMarkerGivesTheOffsetOfTheTag() {
    val e = assertThrows(IllegalArgumentException::class.java) { HighlightingMarkup.parse("<error>ab</warning>") }
    assertEquals("</warning> at offset 9 of the marked text closes <error>", e.message)
  }
}
