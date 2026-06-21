// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import java.awt.Color
import javax.swing.UIManager

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-pi.spec.md

internal data class PiThemeSnapshot(
  val formatVersion: Int,
  val themeId: String,
  val themeName: String,
  val dark: Boolean,
  val fg: Map<String, String>,
  val bg: Map<String, String>,
)

internal fun PiThemeSnapshot.toJsonString(): String {
  return buildString {
    append('{')
    appendJsonProperty("formatVersion", formatVersion)
    append(',')
    appendJsonProperty("themeId", themeId)
    append(',')
    appendJsonProperty("themeName", themeName)
    append(',')
    appendJsonProperty("dark", dark)
    append(',')
    appendJsonProperty("fg", fg)
    append(',')
    appendJsonProperty("bg", bg)
    append('}')
  }
}

internal data class PiIdeThemeInfo(
  val id: String,
  val name: String,
  val dark: Boolean,
) {
  val variant: PiThemeVariant = PiThemeVariant.resolve(id = id, name = name, dark = dark)
}

internal data class PiEditorThemeColors(
  val defaultForeground: Color,
  val defaultBackground: Color,
  val terminalForeground: Color,
  val terminalBackground: Color,
  val caretRow: Color?,
  val selectionBackground: Color?,
  val addedLines: Color?,
  val deletedLines: Color?,
  val hyperlink: Color?,
  val comment: Color?,
  val keyword: Color?,
  val function: Color?,
  val variable: Color?,
  val string: Color?,
  val number: Color?,
  val type: Color?,
  val operator: Color?,
  val punctuation: Color?,
)

internal enum class PiThemeVariant(
  val fallbackThemeId: String,
  val fallbackThemeName: String,
  val dark: Boolean,
  val fallback: PiThemeFallbackPalette,
) {
  ISLANDS_DARK(
    fallbackThemeId = "islands-dark",
    fallbackThemeName = "Islands Dark",
    dark = true,
    fallback = PiThemeFallbackPalette(
      accent = "#3574F0",
      border = "#4E5157",
      borderAccent = "#548AF7",
      borderMuted = "#393B40",
      success = "#6AAB73",
      error = "#DB5C5C",
      warning = "#C9A26D",
      muted = "#B0B7C3",
      dim = "#6F737A",
      text = "#DFE1E5",
      thinkingHigh = "#A571E6",
      selectedBg = "#2E436E",
      userMessageBg = "#212326",
      customMessageBg = "#302A3F",
      toolPendingBg = "#25272B",
      toolSuccessBg = "#253527",
      toolErrorBg = "#3D2828",
      syntaxComment = "#7A7E85",
      syntaxKeyword = "#CF8E6D",
      syntaxFunction = "#56A8F5",
      syntaxVariable = "#DFE1E5",
      syntaxString = "#6AAB73",
      syntaxNumber = "#2AACB8",
      syntaxType = "#B3AE60",
      syntaxOperator = "#B0B7C3",
      syntaxPunctuation = "#B0B7C3",
    ),
  ),
  ISLANDS_LIGHT(
    fallbackThemeId = "islands-light",
    fallbackThemeName = "Islands Light",
    dark = false,
    fallback = PiThemeFallbackPalette(
      accent = "#3574F0",
      border = "#C9CCD6",
      borderAccent = "#3574F0",
      borderMuted = "#DFE1E5",
      success = "#208A3C",
      error = "#C7222D",
      warning = "#8F6500",
      muted = "#5F6570",
      dim = "#8C929C",
      text = "#1F2328",
      thinkingHigh = "#6C4EBB",
      selectedBg = "#D4E5FF",
      userMessageBg = "#F7F8FA",
      customMessageBg = "#F0EBFA",
      toolPendingBg = "#F7F8FA",
      toolSuccessBg = "#EAF6ED",
      toolErrorBg = "#FCECEC",
      syntaxComment = "#8C8C8C",
      syntaxKeyword = "#0033B3",
      syntaxFunction = "#00627A",
      syntaxVariable = "#1F2328",
      syntaxString = "#067D17",
      syntaxNumber = "#1750EB",
      syntaxType = "#2E7D32",
      syntaxOperator = "#1F2328",
      syntaxPunctuation = "#5F6570",
    ),
  ),
  ISLANDS_DARCULA(
    fallbackThemeId = "islands-darcula",
    fallbackThemeName = "Islands Darcula",
    dark = true,
    fallback = PiThemeFallbackPalette(
      accent = "#4E8CDB",
      border = "#5A5D63",
      borderAccent = "#4E8CDB",
      borderMuted = "#3D3F41",
      success = "#57965C",
      error = "#DB5C5C",
      warning = "#C9A26D",
      muted = "#B4B8BF",
      dim = "#6F737A",
      text = "#D3D3D3",
      thinkingHigh = "#A571E6",
      selectedBg = "#365880",
      userMessageBg = "#2B2B2B",
      customMessageBg = "#302A3F",
      toolPendingBg = "#323438",
      toolSuccessBg = "#253527",
      toolErrorBg = "#3D2828",
      syntaxComment = "#7A7E85",
      syntaxKeyword = "#CF8E6D",
      syntaxFunction = "#56A8F5",
      syntaxVariable = "#D3D3D3",
      syntaxString = "#6AAB73",
      syntaxNumber = "#2AACB8",
      syntaxType = "#B3AE60",
      syntaxOperator = "#B4B8BF",
      syntaxPunctuation = "#B4B8BF",
    ),
  ),
  HIGH_CONTRAST(
    fallbackThemeId = "high-contrast",
    fallbackThemeName = "High Contrast",
    dark = true,
    fallback = PiThemeFallbackPalette(
      accent = "#1AEBFF",
      border = "#C4C4C4",
      borderAccent = "#1AEBFF",
      borderMuted = "#6A6173",
      success = "#50A661",
      error = "#FA3232",
      warning = "#E0861F",
      muted = "#E6E6E6",
      dim = "#D38B35",
      text = "#FFFFFF",
      thinkingHigh = "#BBACF9",
      selectedBg = "#3333FF",
      userMessageBg = "#000000",
      customMessageBg = "#000000",
      toolPendingBg = "#000000",
      toolSuccessBg = "#002B0A",
      toolErrorBg = "#330000",
      syntaxComment = "#7EC3E6",
      syntaxKeyword = "#FFCC00",
      syntaxFunction = "#EDB98E",
      syntaxVariable = "#FFFFFF",
      syntaxString = "#A8CC5C",
      syntaxNumber = "#3DB8FF",
      syntaxType = "#73D0E6",
      syntaxOperator = "#FFFFFF",
      syntaxPunctuation = "#FFFFFF",
    ),
  );

  companion object {
    fun resolve(id: String, name: String, dark: Boolean): PiThemeVariant {
      return when (id.ifBlank { name }) {
        "JetBrainsHighContrastTheme", "High Contrast" -> HIGH_CONTRAST
        "Islands Darcula", "Darcula" -> ISLANDS_DARCULA
        "Islands Light", "ExperimentalLight", "ExperimentalLightWithLightHeader", "JetBrainsLightTheme", "IntelliJ" -> ISLANDS_LIGHT
        "Islands Dark", "ExperimentalDark" -> ISLANDS_DARK
        else -> if (dark) ISLANDS_DARK else ISLANDS_LIGHT
      }
    }
  }
}

internal data class PiThemeFallbackPalette(
  val accent: String,
  val border: String,
  val borderAccent: String,
  val borderMuted: String,
  val success: String,
  val error: String,
  val warning: String,
  val muted: String,
  val dim: String,
  val text: String,
  val thinkingHigh: String,
  val selectedBg: String,
  val userMessageBg: String,
  val customMessageBg: String,
  val toolPendingBg: String,
  val toolSuccessBg: String,
  val toolErrorBg: String,
  val syntaxComment: String,
  val syntaxKeyword: String,
  val syntaxFunction: String,
  val syntaxVariable: String,
  val syntaxString: String,
  val syntaxNumber: String,
  val syntaxType: String,
  val syntaxOperator: String,
  val syntaxPunctuation: String,
)

internal class PiThemeSnapshotBuilder(
  private val ideThemeProvider: () -> PiIdeThemeInfo? = ::currentIdeThemeInfo,
  private val editorThemeProvider: () -> PiEditorThemeColors = ::currentEditorThemeColors,
  private val uiColorProvider: (String) -> Color? = ::uiColor,
) {
  fun buildSnapshot(): PiThemeSnapshot {
    val editorTheme = editorThemeProvider()
    val terminalBackground = editorTheme.terminalBackground
    val terminalForeground = editorTheme.terminalForeground
    val ideTheme = ideThemeProvider() ?: PiIdeThemeInfo(
      id = if (isDark(terminalBackground)) PiThemeVariant.ISLANDS_DARK.fallbackThemeId else PiThemeVariant.ISLANDS_LIGHT.fallbackThemeId,
      name = if (isDark(terminalBackground)) PiThemeVariant.ISLANDS_DARK.fallbackThemeName else PiThemeVariant.ISLANDS_LIGHT.fallbackThemeName,
      dark = isDark(terminalBackground),
    )
    val variant = ideTheme.variant
    val fallback = variant.fallback
    val accent = uiColor("Component.focusColor", "Focus.color", "Button.default.focusColor") ?: fallback.accent.toColor()
    val success = uiColor("Label.successForeground") ?: fallback.success.toColor()
    val error = uiColor("Label.errorForeground", "Component.errorFocusColor", "ValidationTooltip.errorForeground")
                ?: fallback.error.toColor()
    val warning = uiColor("Label.warningForeground", "Component.warningFocusColor", "ValidationTooltip.warningForeground")
                  ?: fallback.warning.toColor()
    val muted = uiColor("Component.infoForeground", "Label.infoForeground") ?: fallback.muted.toColor()
    val dim = uiColor("Label.disabledForeground", "Component.disabledText") ?: fallback.dim.toColor()
    val border = uiColor("Component.borderColor", "Separator.foreground", "Popup.borderColor") ?: fallback.border.toColor()
    val borderMuted = uiColor("Component.disabledBorderColor", "Separator.foreground") ?: fallback.borderMuted.toColor()
    val selectedBg = editorTheme.selectionBackground
                     ?: uiColor("List.selectionBackground", "Tree.selectionBackground", "Table.selectionBackground")
                     ?: fallback.selectedBg.toColor()
    val userMessageBg = editorTheme.caretRow?.takeIf { it != terminalBackground }
                        ?: uiColor("Panel.background", "TextArea.background")
                        ?: fallback.userMessageBg.toColor()
    val customMessageBg = uiColor("Banner.aiBackground", "Notification.background") ?: blend(accent, terminalBackground, 0.12)
    val pendingBg = uiColor("ToolWindow.background", "Panel.background") ?: fallback.toolPendingBg.toColor()

    val fg = linkedMapOf(
      "accent" to accent,
      "border" to border,
      "borderAccent" to accent,
      "borderMuted" to borderMuted,
      "success" to success,
      "error" to error,
      "warning" to warning,
      "muted" to muted,
      "dim" to dim,
      "text" to terminalForeground,
      "thinkingText" to muted,
      "userMessageText" to terminalForeground,
      "customMessageText" to terminalForeground,
      "customMessageLabel" to (uiColor("Component.linkColor", "Link.activeForeground") ?: accent),
      "toolTitle" to terminalForeground,
      "toolOutput" to muted,
      "mdHeading" to warning,
      "mdLink" to (editorTheme.hyperlink ?: accent),
      "mdLinkUrl" to muted,
      "mdCode" to (editorTheme.type ?: editorTheme.number ?: accent),
      "mdCodeBlock" to (editorTheme.string ?: terminalForeground),
      "mdCodeBlockBorder" to border,
      "mdQuote" to muted,
      "mdQuoteBorder" to borderMuted,
      "mdHr" to border,
      "mdListBullet" to accent,
      "toolDiffAdded" to (editorTheme.addedLines ?: success),
      "toolDiffRemoved" to (editorTheme.deletedLines ?: error),
      "toolDiffContext" to muted,
      "syntaxComment" to (editorTheme.comment ?: fallback.syntaxComment.toColor()),
      "syntaxKeyword" to (editorTheme.keyword ?: fallback.syntaxKeyword.toColor()),
      "syntaxFunction" to (editorTheme.function ?: fallback.syntaxFunction.toColor()),
      "syntaxVariable" to (editorTheme.variable ?: fallback.syntaxVariable.toColor()),
      "syntaxString" to (editorTheme.string ?: fallback.syntaxString.toColor()),
      "syntaxNumber" to (editorTheme.number ?: fallback.syntaxNumber.toColor()),
      "syntaxType" to (editorTheme.type ?: fallback.syntaxType.toColor()),
      "syntaxOperator" to (editorTheme.operator ?: fallback.syntaxOperator.toColor()),
      "syntaxPunctuation" to (editorTheme.punctuation ?: fallback.syntaxPunctuation.toColor()),
      "thinkingOff" to borderMuted,
      "thinkingMinimal" to dim,
      "thinkingLow" to accent,
      "thinkingMedium" to (editorTheme.type ?: accent),
      "thinkingHigh" to fallback.thinkingHigh.toColor(),
      "thinkingXhigh" to error,
      "bashMode" to success,
    ).mapValues { (_, color) -> color.toPiHex(terminalBackground) }

    val bg = linkedMapOf(
      "selectedBg" to selectedBg,
      "userMessageBg" to userMessageBg,
      "customMessageBg" to customMessageBg,
      "toolPendingBg" to pendingBg,
      "toolSuccessBg" to (uiColor("Banner.successBackground") ?: blend(success, terminalBackground, if (ideTheme.dark) 0.18 else 0.10)),
      "toolErrorBg" to (uiColor("Banner.errorBackground") ?: blend(error, terminalBackground, if (ideTheme.dark) 0.18 else 0.08)),
    ).mapValues { (_, color) -> color.toPiHex(terminalBackground) }

    return PiThemeSnapshot(
      formatVersion = PI_THEME_STATE_FORMAT_VERSION,
      themeId = ideTheme.id.ifBlank { variant.fallbackThemeId },
      themeName = ideTheme.name.ifBlank { variant.fallbackThemeName },
      dark = ideTheme.dark,
      fg = fg,
      bg = bg,
    )
  }

  private fun uiColor(vararg keys: String): Color? {
    for (key in keys) {
      val color = uiColorProvider(key)
      if (color != null) return color
    }
    return null
  }
}

private fun currentIdeThemeInfo(): PiIdeThemeInfo? {
  val theme = LafManager.getInstance().currentUIThemeLookAndFeel ?: return null
  return theme.toPiIdeThemeInfo()
}

private fun UIThemeLookAndFeelInfo.toPiIdeThemeInfo(): PiIdeThemeInfo = PiIdeThemeInfo(id = id, name = name, dark = isDark)

private fun currentEditorThemeColors(): PiEditorThemeColors {
  val scheme = EditorColorsManager.getInstance().globalScheme
  return scheme.toPiEditorThemeColors()
}

// External names of ConsoleViewContentType.NORMAL_OUTPUT_KEY / CONSOLE_BACKGROUND_KEY, which the IDE
// terminal uses for its default colors; the declaring class lives in a module this one does not depend on.
private val TERMINAL_FOREGROUND_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_NORMAL_OUTPUT")
private val TERMINAL_BACKGROUND_KEY = ColorKey.createColorKey("CONSOLE_BACKGROUND_KEY")

internal fun EditorColorsScheme.toPiEditorThemeColors(): PiEditorThemeColors {
  return PiEditorThemeColors(
    defaultForeground = defaultForeground,
    defaultBackground = defaultBackground,
    terminalForeground = foreground(TERMINAL_FOREGROUND_KEY) ?: defaultForeground,
    terminalBackground = getColor(TERMINAL_BACKGROUND_KEY) ?: defaultBackground,
    caretRow = getColor(EditorColors.CARET_ROW_COLOR),
    selectionBackground = getColor(EditorColors.SELECTION_BACKGROUND_COLOR),
    addedLines = getColor(EditorColors.ADDED_LINES_COLOR),
    deletedLines = getColor(EditorColors.DELETED_LINES_COLOR),
    hyperlink = foreground(CodeInsightColors.HYPERLINK_ATTRIBUTES) ?: foreground(EditorColors.REFERENCE_HYPERLINK_COLOR),
    comment = foreground(DefaultLanguageHighlighterColors.LINE_COMMENT) ?: foreground(DefaultLanguageHighlighterColors.BLOCK_COMMENT),
    keyword = foreground(DefaultLanguageHighlighterColors.KEYWORD),
    function = foreground(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
               ?: foreground(DefaultLanguageHighlighterColors.FUNCTION_CALL),
    variable = foreground(DefaultLanguageHighlighterColors.LOCAL_VARIABLE) ?: foreground(DefaultLanguageHighlighterColors.IDENTIFIER),
    string = foreground(DefaultLanguageHighlighterColors.STRING),
    number = foreground(DefaultLanguageHighlighterColors.NUMBER),
    type = foreground(DefaultLanguageHighlighterColors.CLASS_NAME) ?: foreground(DefaultLanguageHighlighterColors.CLASS_REFERENCE),
    operator = foreground(DefaultLanguageHighlighterColors.OPERATION_SIGN),
    punctuation = foreground(DefaultLanguageHighlighterColors.COMMA) ?: foreground(DefaultLanguageHighlighterColors.DOT),
  )
}

private fun EditorColorsScheme.foreground(key: TextAttributesKey): Color? = getAttributes(key)?.foregroundColor

private fun uiColor(key: String): Color? = UIManager.getColor(key)

private fun Color.toPiHex(background: Color): String {
  val color = if (alpha == 255) this else blend(this, background, alpha / 255.0)
  return "#%02X%02X%02X".format(color.red, color.green, color.blue)
}

private fun String.toColor(): Color = Color(Integer.parseInt(removePrefix("#"), 16))

private fun blend(foreground: Color, background: Color, foregroundFraction: Double): Color {
  val ratio = foregroundFraction.coerceIn(0.0, 1.0)
  return Color(
    (foreground.red * ratio + background.red * (1 - ratio)).toInt().coerceIn(0, 255),
    (foreground.green * ratio + background.green * (1 - ratio)).toInt().coerceIn(0, 255),
    (foreground.blue * ratio + background.blue * (1 - ratio)).toInt().coerceIn(0, 255),
  )
}

private fun isDark(color: Color): Boolean {
  return color.red * 0.299 + color.green * 0.587 + color.blue * 0.114 < 128
}

private fun StringBuilder.appendJsonProperty(name: String, value: Int) {
  appendJsonString(name)
  append(':')
  append(value)
}

private fun StringBuilder.appendJsonProperty(name: String, value: Boolean) {
  appendJsonString(name)
  append(':')
  append(value)
}

private fun StringBuilder.appendJsonProperty(name: String, value: String) {
  appendJsonString(name)
  append(':')
  appendJsonString(value)
}

private fun StringBuilder.appendJsonProperty(name: String, value: Map<String, String>) {
  appendJsonString(name)
  append(':')
  append('{')
  var first = true
  for ((mapKey, mapValue) in value) {
    if (first) {
      first = false
    }
    else {
      append(',')
    }
    appendJsonString(mapKey)
    append(':')
    appendJsonString(mapValue)
  }
  append('}')
}

private fun StringBuilder.appendJsonString(value: String) {
  append('"')
  for (char in value) {
    when (char) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      '\b' -> append("\\b")
      '\u000C' -> append("\\f")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> {
        if (char < ' ') {
          append("\\u")
          append(char.code.toString(16).padStart(4, '0'))
        }
        else {
          append(char)
        }
      }
    }
  }
  append('"')
}

internal const val PI_THEME_STATE_FORMAT_VERSION: Int = 1
