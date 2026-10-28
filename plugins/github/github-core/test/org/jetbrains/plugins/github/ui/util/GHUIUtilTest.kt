// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.util

import com.intellij.ui.JBColor
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.github.api.data.GHLabel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color

class GHUIUtilTest {
  private var wasDarkTheme = false

  @BeforeEach
  fun setUp() {
    wasDarkTheme = !JBColor.isBright()
  }

  @AfterEach
  fun tearDown() {
    JBColor.setDark(wasDarkTheme)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("labelColors")
  fun `label foreground color`(hexColor: String, foreground: Color) {
    JBColor.setDark(false)

    val label = GHUIUtil.createIssueLabelLabel(GHLabel("id", "", "label", hexColor))
    assertThat(label.foreground).isEqualTo(foreground)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("labelColorsDark")
  fun `label foreground color in dark theme`(hexColor: String, foreground: Color) {
    JBColor.setDark(true)

    val label = GHUIUtil.createIssueLabelLabel(GHLabel("id", "", "label", hexColor))
    assertThat(label.foreground).isEqualTo(foreground)
  }

  companion object {
    private val COLORS_LIGHT_DARK = listOf(
      "d73a4a" to (Color.BLACK to Color.WHITE),
      "0075ca" to (Color.WHITE to Color.WHITE),
      "cfd3d7" to (Color.BLACK to Color.BLACK),
      "a2eeef" to (Color.BLACK to Color.BLACK),
      "7057ff" to (Color.WHITE to Color.WHITE),
      "008672" to (Color.BLACK to Color.WHITE),
      "e4e669" to (Color.BLACK to Color.BLACK),
      "d876e3" to (Color.BLACK to Color.BLACK),
      "ffffff" to (Color.BLACK to Color.BLACK)
    )

    @JvmStatic
    fun labelColors(): List<Arguments> = COLORS_LIGHT_DARK.map { color ->
      Arguments.of(color.first, color.second.first)
    }

    @JvmStatic
    fun labelColorsDark(): List<Arguments> = COLORS_LIGHT_DARK.map { color ->
      Arguments.of(color.first, color.second.second)
    }
  }
}
