// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.core

import junit.framework.TestCase.assertEquals
import org.junit.Test

class EditorConfigAutomatonBuilderTest {
  @Test
  fun `test getGlob on simple text`() {
    val source = "hello?*[abc]"
    val actual = EditorConfigAutomatonBuilder.sanitizeGlob(source, "C:/folder")
    val expected = ".*/hello.[^/]*[abc]"
    assertEquals(expected, actual)
  }

  @Test
  fun `test getGlob on char negation`() {
    val source = "abc[!abc]"
    val actual = EditorConfigAutomatonBuilder.sanitizeGlob(source, "C:/folder")
    val expected = ".*/abc[^abc]"
    assertEquals(expected, actual)
  }

  @Test
  fun `test getGlob on pattern enumeration`() {
    val source = "*.{cs, vb}"
    val actual = EditorConfigAutomatonBuilder.sanitizeGlob(source, "C:/folder")
    val expected = ".*/[^/]*\\.(cs|vb)"
    assertEquals(expected, actual)
  }

  @Test
  fun `test getGlob on global pattern enumeration`() {
    val source = "{*.cs, *.vb}"
    val actual = EditorConfigAutomatonBuilder.sanitizeGlob(source, "C:/folder")
    val expected = ".*/([^/]*\\.cs|[^/]*\\.vb)"
    assertEquals(expected, actual)
  }

  @Test
  fun `test getGlob on pattern with separator`() {
    val source = "/abc/*.txt"
    val actual = EditorConfigAutomatonBuilder.sanitizeGlob(source, "C:/folder")
    val expected = "C\\:/folder/abc/[^/]*\\.txt"
    assertEquals(expected, actual)
  }

  @Test
  fun testHeaderTrimming() {
    val text = "[foo]"
    val newText = text.substring(1, text.length - 1)
    val expected = "foo"
    assertEquals(expected, newText)
  }
}
