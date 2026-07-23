// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider.FAKE_LINE_NUMBER
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the line mapping that lets the "Your uncommitted changes" side of a patch-conflict merge be annotated against
 * its base revision: context lines map to base-revision lines, lines added on top of the base render as "not committed".
 */
@TestApplication
class PatchBaseLineNumberProviderTest {
  @Test
  fun `context lines map to base, inserted lines are fake`() {
    // base:    A B C
    // patched: A X B C Y   (X inserted after A, Y appended)
    val provider = createProvider(base = "A\nB\nC", patched = "A\nX\nB\nC\nY")

    assertEquals(0, provider.getLineNumber(0)) // A -> base A
    assertEquals(FAKE_LINE_NUMBER, provider.getLineNumber(1)) // X is new
    assertTrue(provider.isLineChanged(1))
    assertEquals(1, provider.getLineNumber(2)) // B -> base B
    assertEquals(2, provider.getLineNumber(3)) // C -> base C
    assertEquals(FAKE_LINE_NUMBER, provider.getLineNumber(4)) // Y is new
    assertTrue(provider.isLineChanged(4))
  }

  @Test
  fun `identical content maps one to one`() {
    val provider = createProvider(base = "L1\nL2\nL3\nL4", patched = "L1\nL2\nL3\nL4")

    assertEquals(4, provider.lineCount)
    for (line in 0 until provider.lineCount) {
      assertEquals(line, provider.getLineNumber(line))
      assertFalse(provider.isLineChanged(line))
    }
    assertFalse(provider.isRangeChanged(0, 3))
  }

  @Test
  fun `changed line is reported as not committed`() {
    // middle line modified -> cannot be attributed to a base commit
    val provider = createProvider(base = "A\nB\nC", patched = "A\nB2\nC")

    assertEquals(0, provider.getLineNumber(0))
    assertTrue(provider.isLineChanged(1))
    assertEquals(2, provider.getLineNumber(2))
    assertTrue(provider.isRangeChanged(0, 2))
  }

  @Test
  fun `line count reports the base revision size, not the patched size`() {
    // base has 3 lines; the uncommitted content is much larger. getLineCount() must report the base (annotated) size
    // so that AnnotateWarningsService does not flag a spurious line-count mismatch.
    val provider = createProvider(base = "A\nB\nC", patched = "A\nX\nY\nB\nZ\nC\nW")

    assertEquals(3, provider.lineCount)
  }

  @Test
  fun `added lines expose not-committed placeholder text, context lines do not`() {
    // base:    A B C
    // patched: A X B C Y
    val provider = createProvider(base = "A\nB\nC", patched = "A\nX\nB\nC\nY")
      as AnnotationGutterLineConvertorProxy.NonAnnotatedLineTextProvider

    assertNull(provider.getNonAnnotatedLineText(0)) // A - context, has base blame
    assertEquals("Not committed yet", provider.getNonAnnotatedLineText(1)) // X - added
    assertNull(provider.getNonAnnotatedLineText(2)) // B - context
    assertEquals("Not committed yet", provider.getNonAnnotatedLineText(4)) // Y - added
  }

  private fun createProvider(base: String, patched: String): UpToDateLineNumberProvider {
    val document = EditorFactory.getInstance().createDocument(patched)
    return AnnotateDiffViewerAction.createPatchBaseLineNumberProvider(base, document)!!
  }
}
