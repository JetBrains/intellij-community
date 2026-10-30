// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.HunkName
import com.intellij.openapi.vcs.changes.HunkNamePart
import com.intellij.platform.vcs.impl.shared.rpc.StructuralHunkDto
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.SimpleTextAttributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
internal class StructuralHunkPresentationTest {
  @Test
  fun `the row colors each part by its change and shows the old name and the detail`() {
    val parts = listOf(
      HunkNamePart("added()", FileStatus.ADDED),
      HunkNamePart("renamed()", FileStatus.MODIFIED, previousText = "old()"),
      HunkNamePart("deleted()", FileStatus.DELETED),
    )
    val hunk = hunk(parts, detail = HunkNamePart("in Owner", FileStatus.MODIFIED))

    val fragments = StructuralHunkPresentation.fragments(hunk)

    assertEquals(listOf("added()", ", ", "old()", " → ", "renamed()", ", ", "deleted()", "  in Owner"), fragments.map { it.text })
    assertEquals(SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.ADDED.color), fragments[0].attributes)
    assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, fragments[1].attributes)
    assertEquals(SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, FileStatus.DELETED.color), fragments[2].attributes, "The old name")
    assertEquals(SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.MODIFIED.color), fragments[4].attributes)
    assertEquals(SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, FileStatus.DELETED.color), fragments[6].attributes)
    assertEquals(SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor), fragments[7].attributes)
    assertEquals("added(), old() → renamed(), deleted()", StructuralHunkPresentation.text(hunk))
    assertEquals(listOf("added()", "old() → renamed()", "deleted()", "in Owner"), tooltipLines(hunk))
  }

  @Test
  fun `the row counts the parts that do not fit and marks a change of whitespace`() {
    val hunk = hunk((0 until 30).map { HunkNamePart("element$it", FileStatus.MODIFIED) }, isWhitespaceOnly = true)

    val texts = StructuralHunkPresentation.fragments(hunk).map { it.text }

    val shown = (0 until 8).map { "element$it" }
    assertEquals(shown, texts.filterIndexed { index, _ -> index % 2 == 0 }.take(shown.size))
    assertEquals(listOf(" and 22 more", "  whitespace only"), texts.takeLast(2))
    assertEquals(shown.joinToString(", ") + " and 22 more", StructuralHunkPresentation.text(hunk))
    assertEquals((0 until 20).map { "element$it" } + "and 10 more", tooltipLines(hunk))
  }

  @Test
  fun `a hunk of blank lines has a gray row with the text icon and no tooltip`() {
    val hunk = hunk(emptyList())

    assertEquals(listOf(StructuralHunkFragment("Blank lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)), StructuralHunkPresentation.fragments(hunk))
    assertEquals("Blank lines", StructuralHunkPresentation.text(hunk))
    assertNull(StructuralHunkPresentation.tooltip(hunk))
    assertSame(AllIcons.FileTypes.Text, StructuralHunkPresentation.icon(hunk))
  }

  @Test
  fun `the hunk keeps its lines and the icon of its element`() {
    val hunk = StructuralHunkDto.of(HunkLines(2, 4, 3, 6), HunkName(listOf(HunkNamePart("method()", FileStatus.MODIFIED)), null, AllIcons.Nodes.Method))

    assertEquals(listOf(2, 4, 3, 6), listOf(hunk.baseStartLine, hunk.baseEndLine, hunk.currentStartLine, hunk.currentEndLine))
    assertSame(AllIcons.Nodes.Method, StructuralHunkPresentation.icon(hunk))
  }

  private fun hunk(parts: List<HunkNamePart>, detail: HunkNamePart? = null, icon: Icon? = null, isWhitespaceOnly: Boolean = false) =
    StructuralHunkDto.of(HunkLines(0, 1, 0, 1), HunkName(parts, detail, icon, isWhitespaceOnly))

  private fun tooltipLines(hunk: StructuralHunkDto): List<String> =
    checkNotNull(StructuralHunkPresentation.tooltip(hunk)).toString().removePrefix("<html><body>").removeSuffix("</body></html>").split(Regex("<br/?>"))
}
