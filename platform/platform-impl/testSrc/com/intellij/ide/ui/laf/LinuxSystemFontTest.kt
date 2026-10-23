// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.awt.Font

class LinuxSystemFontTest {
  @Test
  fun `the size follows the Xft dpi when the JRE does not scale`() {
    val font = parsePangoFont("Cantarell Bold 11", xftDpi = 192 * 1024, uiScaleEnabled = { false })
    assertThat(font.name).isEqualTo("Cantarell")
    assertThat(font.style).isEqualTo(Font.BOLD)
    assertThat(font.size2D).isCloseTo(11 * 192 / 72f, within(0.01f))
  }

  @Test
  fun `the size stays at 96 dpi when the JRE scales`() {
    val font = parsePangoFont("Ubuntu Italic 10", xftDpi = 192 * 1024, uiScaleEnabled = { true })
    assertThat(font.name).isEqualTo("Ubuntu")
    assertThat(font.style).isEqualTo(Font.ITALIC)
    assertThat(font.size2D).isCloseTo(10 * 96 / 72f, within(0.01f))
  }

  @Test
  fun `a pango logical family maps to the java logical font`() {
    val font = parsePangoFont("monospace 9", xftDpi = -1 * 1024) { false }
    assertThat(font.name).isEqualTo("Monospaced")
    assertThat(font.size2D).isCloseTo(9 * 96 / 72f, within(0.01f))
  }

  @Test
  fun `the dpi is clamped at 50`() {
    val font = parsePangoFont("sans 10", xftDpi = 30 * 1024) { false }
    assertThat(font.name).isEqualTo("SansSerif")
    assertThat(font.size2D).isCloseTo(10 * 50 / 72f, within(0.01f))
  }

  @Test
  fun `the settings ini yields the font name and the dpi of its Settings section`() {
    val ini = parseGtkSettingsIni("""
      # a comment
      [Settings]
      gtk-font-name = "Noto Sans 11"
      gtk-xft-dpi=98304
      [Other]
      gtk-font-name=Ignored 9
    """.trimIndent())
    assertThat(ini).containsEntry("gtk-font-name", "Noto Sans 11").containsEntry("gtk-xft-dpi", "98304").hasSize(2)
  }
}
