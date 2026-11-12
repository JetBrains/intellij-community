// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform

internal class FontInfoLetterSpacingTest {
  @Test
  fun `with fractional metrics the letter spacing keeps its exact pixels`() {
    val context = FontRenderContext(null, true, true)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, context)).isCloseTo(0.65f, within(0.0001f))
  }

  @Test
  fun `without fractional metrics the letter spacing rounds to whole device pixels`() {
    val context = FontRenderContext(null, true, false)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, context)).isEqualTo(1f)
    assertThat(FontInfo.letterSpacingPixels(0.02f, 13f, context)).isEqualTo(0f)
    assertThat(FontInfo.letterSpacingPixels(-0.05f, 13f, context)).isEqualTo(-1f)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, null)).isEqualTo(1f)
  }

  @Test
  fun `on a scaled context the letter spacing rounds to whole device pixels, not to user space pixels`() {
    val retina = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), true, false)
    assertThat(FontInfo.letterSpacingPixels(0.05f, 13f, retina)).isEqualTo(0.5f)
  }
}
