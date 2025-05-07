// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.DefaultAsserter.assertEquals

class ComposeResourcesParsingTest {

  @Test
  fun `test ResourceType parsing`() {
    val stringType = ResourceType.fromString("string")
    assertEquals("different types", ResourceType.STRING, stringType)
    assertTrue(stringType.isStringType)

    val drawableType = ResourceType.fromString("drawable")
    assertEquals("different types", ResourceType.DRAWABLE, drawableType)
    assertFalse(drawableType.isStringType)

    val fontType = ResourceType.fromString("font")
    assertEquals("different types", ResourceType.FONT, fontType)
    assertFalse(drawableType.isStringType)

    val unknownResult = runCatching { ResourceType.fromString("unknown") }
    assertEquals("exception message", "Unknown resource type: 'unknown'.", unknownResult.exceptionOrNull()!!.message)
  }
}
