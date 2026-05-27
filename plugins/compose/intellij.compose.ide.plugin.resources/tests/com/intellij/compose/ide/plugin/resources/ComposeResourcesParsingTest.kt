// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
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

  @Test
  fun `test ResourceType parsing from qualified resource directories`() {
    assertEquals(
      "different types",
      ResourceType.DRAWABLE,
      ResourceType.fromPath(Path.of("/project/src/commonMain/composeResources/drawable-xxhdpi/icon.xml")),
    )
    assertEquals(
      "different types",
      ResourceType.STRING,
      ResourceType.fromPath(Path.of("/project/src/commonMain/composeResources/values-nl/strings.xml")),
    )
    assertEquals(
      "different types",
      ResourceType.FONT,
      ResourceType.fromPath(Path.of("/project/src/commonMain/composeResources/font-ro/font.ttf")),
    )
  }
}
