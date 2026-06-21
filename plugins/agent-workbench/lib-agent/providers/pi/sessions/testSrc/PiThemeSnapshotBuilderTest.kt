// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiThemeSnapshotBuilderTest {
  @Test
  fun resolvesMainIdeaAppearanceThemeVariants() {
    assertThat(PiThemeVariant.resolve("Islands Dark", "Islands Dark", dark = true)).isEqualTo(PiThemeVariant.ISLANDS_DARK)
    assertThat(PiThemeVariant.resolve("Islands Light", "Islands Light", dark = false)).isEqualTo(PiThemeVariant.ISLANDS_LIGHT)
    assertThat(PiThemeVariant.resolve("Islands Darcula", "Islands Darcula", dark = true)).isEqualTo(PiThemeVariant.ISLANDS_DARCULA)
    assertThat(PiThemeVariant.resolve("JetBrainsHighContrastTheme", "High Contrast", dark = true)).isEqualTo(PiThemeVariant.HIGH_CONTRAST)
  }

  @Test
  fun usesRuntimeUiAndEditorColorsBeforeFallbackPalette() {
    val snapshot = PiThemeSnapshotBuilder(
      ideThemeProvider = { PiIdeThemeInfo(id = "JetBrainsHighContrastTheme", name = "High Contrast", dark = true) },
      editorThemeProvider = { editorThemeColors() },
      uiColorProvider = { key -> UI_COLORS[key] },
    ).buildSnapshot()

    assertThat(snapshot.formatVersion).isEqualTo(PI_THEME_STATE_FORMAT_VERSION)
    assertThat(snapshot.themeId).isEqualTo("JetBrainsHighContrastTheme")
    assertThat(snapshot.themeName).isEqualTo("High Contrast")
    assertThat(snapshot.dark).isTrue()
    assertThat(snapshot.fg).containsEntry("accent", "#123456")
    assertThat(snapshot.fg).containsEntry("borderAccent", "#123456")
    assertThat(snapshot.fg).containsEntry("success", "#22AA44")
    assertThat(snapshot.fg).containsEntry("error", "#EE3344")
    assertThat(snapshot.fg).containsEntry("warning", "#DDAA22")
    assertThat(snapshot.fg).containsEntry("muted", "#73767C")
    assertThat(snapshot.fg).containsEntry("dim", "#4C4F56")
    assertThat(snapshot.fg).containsEntry("thinkingHigh", PiThemeVariant.HIGH_CONTRAST.fallback.thinkingHigh)
    assertThat(snapshot.fg).containsEntry("text", "#131313")
    assertThat(snapshot.fg).containsEntry("userMessageText", "#131313")
    assertThat(snapshot.fg).containsEntry("syntaxKeyword", "#888888")
    assertThat(snapshot.fg).containsEntry("toolDiffAdded", "#444444")
    assertThat(snapshot.bg).containsEntry("selectedBg", "#333333")
    assertThat(snapshot.bg).containsEntry("userMessageBg", "#2A2A2A")
    assertThat(snapshot.bg).containsEntry("customMessageBg", "#261E3C")
    assertThat(snapshot.bg).containsEntry("toolSuccessBg", "#16261C")
    assertThat(snapshot.bg).containsEntry("toolErrorBg", "#371B1C")
    assertThat(snapshot.fg.keys).containsExactlyElementsOf(PI_THEME_TEST_FG_KEYS)
    assertThat(snapshot.bg.keys).containsExactlyElementsOf(PI_THEME_TEST_BG_KEYS)
  }

  @Test
  fun mapsUnknownCustomThemesByDarkness() {
    val darkSnapshot = PiThemeSnapshotBuilder(
      ideThemeProvider = { PiIdeThemeInfo(id = "custom-dark", name = "Custom Dark", dark = true) },
      editorThemeProvider = { editorThemeColors(defaultBackground = Color(0x101010)) },
      uiColorProvider = { null },
    ).buildSnapshot()
    val lightSnapshot = PiThemeSnapshotBuilder(
      ideThemeProvider = { PiIdeThemeInfo(id = "custom-light", name = "Custom Light", dark = false) },
      editorThemeProvider = { editorThemeColors(defaultBackground = Color(0xFAFAFA)) },
      uiColorProvider = { null },
    ).buildSnapshot()

    assertThat(darkSnapshot.fg).containsEntry("accent", PiThemeVariant.ISLANDS_DARK.fallback.accent)
    assertThat(lightSnapshot.fg).containsEntry("accent", PiThemeVariant.ISLANDS_LIGHT.fallback.accent)
  }

  @Test
  fun fallsBackToPanelBackgroundWhenCaretRowMatchesTerminalBackground() {
    val snapshot = PiThemeSnapshotBuilder(
      ideThemeProvider = { PiIdeThemeInfo(id = "Islands Dark", name = "Islands Dark", dark = true) },
      editorThemeProvider = { editorThemeColors(caretRow = Color(0x222222), terminalBackground = Color(0x222222)) },
      uiColorProvider = { key -> if (key == "Panel.background") Color(0x2B2D30) else null },
    ).buildSnapshot()

    assertThat(snapshot.bg).containsEntry("userMessageBg", "#2B2D30")
  }

  @Test
  fun blendsFallbackBackgroundsAgainstTerminalBackground() {
    val snapshot = PiThemeSnapshotBuilder(
      ideThemeProvider = { PiIdeThemeInfo(id = "Islands Dark", name = "Islands Dark", dark = true) },
      editorThemeProvider = { editorThemeColors(terminalBackground = Color(0x000000)) },
      uiColorProvider = { null },
    ).buildSnapshot()

    // blend(fallback success #6AAB73, terminal background #000000, 0.18)
    assertThat(snapshot.bg).containsEntry("toolSuccessBg", "#131E14")
  }

  companion object {
    private val UI_COLORS = mapOf(
      "Component.focusColor" to Color(0x123456),
      "Label.successForeground" to Color(0x22AA44),
      "Label.errorForeground" to Color(0xEE3344),
      "Label.warningForeground" to Color(0xDDAA22),
      "Component.infoForeground" to Color(0x73767C),
      "Label.disabledForeground" to Color(0x4C4F56),
      "Banner.aiBackground" to Color(0x261E3C),
      "Banner.successBackground" to Color(0x16261C),
      "Banner.errorBackground" to Color(0x371B1C),
    )

    private fun editorThemeColors(
      defaultBackground: Color = Color(0x222222),
      caretRow: Color? = Color(0x2A2A2A),
      terminalForeground: Color = Color(0x131313),
      terminalBackground: Color = defaultBackground,
    ): PiEditorThemeColors {
      return PiEditorThemeColors(
        defaultForeground = Color(0x111111),
        defaultBackground = defaultBackground,
        terminalForeground = terminalForeground,
        terminalBackground = terminalBackground,
        caretRow = caretRow,
        selectionBackground = Color(0x333333),
        addedLines = Color(0x444444),
        deletedLines = Color(0x555555),
        hyperlink = Color(0x666666),
        comment = Color(0x777777),
        keyword = Color(0x888888),
        function = Color(0x999999),
        variable = Color(0xAAAAAA),
        string = Color(0xBBBBBB),
        number = Color(0xCCCCCC),
        type = Color(0xDDDDDD),
        operator = Color(0xEEEEEE),
        punctuation = Color(0xFAFAFA),
      )
    }
  }
}
