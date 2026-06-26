// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-codex-theme.spec.md

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.util.io.DigestUtil
import java.awt.Color
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException

private val CODEX_THEME_LOG = logger<CodexThemeSupport>()

internal data class CodexThemeLaunchConfig(
  val themeFilePath: Path,
  val themeConfigValue: String,
)

internal class CodexThemeSupport(
  private val rootDirectoryProvider: () -> Path = ::defaultCodexThemeRootDirectory,
  private val themeSnapshotProvider: () -> CodexThemeSnapshot = CodexThemeSnapshotBuilder()::buildSnapshot,
) {
  fun launchConfigOrNull(): CodexThemeLaunchConfig? {
    return try {
      materializeLaunchConfig()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      CODEX_THEME_LOG.warn("Failed to materialize Codex theme", e)
      null
    }
  }

  private fun materializeLaunchConfig(): CodexThemeLaunchConfig {
    val content = themeSnapshotProvider().toTmThemeXml()
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    val hash = DigestUtil.sha256Hex(bytes)
    val fileName = CODEX_THEME_FILE_NAME_PREFIX + hash.take(CODEX_THEME_FILE_NAME_HASH_PREFIX_LENGTH) + CODEX_THEME_FILE_EXTENSION
    val rootDirectory = rootDirectoryProvider().toAbsolutePath().normalize()
    val themeFilePath = rootDirectory.resolve(fileName)
    val manifestPath = rootDirectory.resolve(CODEX_THEME_MANIFEST_FILE_NAME)

    Files.createDirectories(rootDirectory)
    val previousManagedFileNames = readManagedThemeFileNames(readStringOrNull(manifestPath))
    if (sha256HexOrNull(themeFilePath) != hash) {
      writeAtomically(themeFilePath, bytes)
    }
    deleteStaleManagedThemeFiles(rootDirectory, previousManagedFileNames, currentFileName = fileName)

    val manifest = buildManifest(fileName, hash)
    writeStringIfChanged(manifestPath, manifest)

    val themePathWithoutExtension = themeFilePath.toString().removeSuffix(CODEX_THEME_FILE_EXTENSION)
    return CodexThemeLaunchConfig(
      themeFilePath = themeFilePath,
      themeConfigValue = "tui.theme=${codexThemeTomlString(themePathWithoutExtension)}",
    )
  }

  companion object {
    val DEFAULT: CodexThemeSupport = CodexThemeSupport()
  }
}

internal data class CodexThemeSnapshot(
  val foreground: String,
  val background: String,
  val selection: String,
  val comment: String,
  val keyword: String,
  val function: String,
  val variable: String,
  val stringLiteral: String,
  val numberLiteral: String,
  val typeName: String,
  val operator: String,
  val punctuation: String,
  val hyperlink: String,
  val heading: String,
  val status: String,
  val insertedBackground: String,
  val deletedBackground: String,
)

internal fun CodexThemeSnapshot.toTmThemeXml(): String {
  return buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" ")
    append("\"https://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
    append("<plist version=\"1.0\">\n")
    append("<dict>\n")
    appendXmlKeyString("name", "Agent Workbench IDEA", indent = "  ")
    append("  <key>settings</key>\n")
    append("  <array>\n")
    for (setting in codexTextMateThemeSettings(this@toTmThemeXml)) {
      appendTextMateSetting(setting)
    }
    append("  </array>\n")
    append("</dict>\n")
    append("</plist>\n")
  }
}

internal data class CodexEditorThemeColors(
  val defaultForeground: Color,
  val defaultBackground: Color,
  val terminalForeground: Color,
  val terminalBackground: Color,
  val selectionBackground: Color?,
  val addedLines: Color?,
  val deletedLines: Color?,
  val hyperlink: Color?,
  val comment: Color?,
  val keyword: Color?,
  val function: Color?,
  val variable: Color?,
  val stringLiteral: Color?,
  val numberLiteral: Color?,
  val typeName: Color?,
  val operator: Color?,
  val punctuation: Color?,
)

internal class CodexThemeSnapshotBuilder(
  private val editorThemeProvider: () -> CodexEditorThemeColors = ::currentCodexEditorThemeColors,
) {
  fun buildSnapshot(): CodexThemeSnapshot {
    val editorTheme = editorThemeProvider()
    val background = editorTheme.terminalBackground
    val dark = isDark(background)
    val fallback = if (dark) CODEX_DARK_FALLBACK else CODEX_LIGHT_FALLBACK
    val accent = fallback.accent.toColor()
    val success = fallback.success.toColor()
    val error = fallback.error.toColor()

    return CodexThemeSnapshot(
      foreground = editorTheme.terminalForeground.toCodexThemeHex(background),
      background = background.toCodexThemeHex(background),
      selection = (editorTheme.selectionBackground ?: blend(accent, background, if (dark) 0.35 else 0.20)).toCodexThemeHex(background),
      comment = editorTheme.comment.toCodexThemeHexOrFallback(fallback.syntaxComment, background),
      keyword = editorTheme.keyword.toCodexThemeHexOrFallback(fallback.syntaxKeyword, background),
      function = editorTheme.function.toCodexThemeHexOrFallback(fallback.syntaxFunction, background),
      variable = editorTheme.variable.toCodexThemeHexOrFallback(fallback.syntaxVariable, background),
      stringLiteral = editorTheme.stringLiteral.toCodexThemeHexOrFallback(fallback.syntaxString, background),
      numberLiteral = editorTheme.numberLiteral.toCodexThemeHexOrFallback(fallback.syntaxNumber, background),
      typeName = editorTheme.typeName.toCodexThemeHexOrFallback(fallback.syntaxType, background),
      operator = editorTheme.operator.toCodexThemeHexOrFallback(fallback.syntaxOperator, background),
      punctuation = editorTheme.punctuation.toCodexThemeHexOrFallback(fallback.syntaxPunctuation, background),
      hyperlink = (editorTheme.hyperlink ?: accent).toCodexThemeHex(background),
      heading = (editorTheme.typeName ?: fallback.heading.toColor()).toCodexThemeHex(background),
      status = (editorTheme.function ?: editorTheme.keyword ?: accent).toCodexThemeHex(background),
      insertedBackground = (editorTheme.addedLines ?: blend(success, background, if (dark) 0.18 else 0.10)).toCodexThemeHex(background),
      deletedBackground = (editorTheme.deletedLines ?: blend(error, background, if (dark) 0.18 else 0.08)).toCodexThemeHex(background),
    )
  }
}

private data class CodexThemeFallbackPalette(
  val accent: String,
  val success: String,
  val error: String,
  val heading: String,
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

private data class CodexTextMateThemeSetting(
  val name: String? = null,
  val scope: String? = null,
  val foreground: String? = null,
  val background: String? = null,
  val selection: String? = null,
  val fontStyle: String? = null,
)

private fun codexTextMateThemeSettings(snapshot: CodexThemeSnapshot): List<CodexTextMateThemeSetting> {
  return listOf(
    CodexTextMateThemeSetting(
      foreground = snapshot.foreground,
      background = snapshot.background,
      selection = snapshot.selection,
    ),
    CodexTextMateThemeSetting(name = "Comment", scope = "comment", foreground = snapshot.comment),
    CodexTextMateThemeSetting(name = "Keyword", scope = "keyword", foreground = snapshot.keyword),
    CodexTextMateThemeSetting(name = "Control Keyword", scope = "keyword.control", foreground = snapshot.keyword),
    CodexTextMateThemeSetting(name = "Storage Type", scope = "storage.type", foreground = snapshot.typeName),
    CodexTextMateThemeSetting(name = "Storage Modifier", scope = "storage.modifier", foreground = snapshot.keyword),
    CodexTextMateThemeSetting(name = "Function", scope = "entity.name.function", foreground = snapshot.function),
    CodexTextMateThemeSetting(name = "Support Function", scope = "support.function", foreground = snapshot.function),
    CodexTextMateThemeSetting(name = "Variable", scope = "variable", foreground = snapshot.variable),
    CodexTextMateThemeSetting(name = "Parameter", scope = "variable.parameter", foreground = snapshot.variable),
    CodexTextMateThemeSetting(name = "String", scope = "string", foreground = snapshot.stringLiteral),
    CodexTextMateThemeSetting(name = "Constant", scope = "constant", foreground = snapshot.numberLiteral),
    CodexTextMateThemeSetting(name = "Numeric Constant", scope = "constant.numeric", foreground = snapshot.numberLiteral),
    CodexTextMateThemeSetting(name = "Language Constant", scope = "constant.language", foreground = snapshot.keyword),
    CodexTextMateThemeSetting(name = "Other Constant", scope = "constant.other", foreground = snapshot.numberLiteral),
    CodexTextMateThemeSetting(name = "Type", scope = "entity.name.type", foreground = snapshot.typeName),
    CodexTextMateThemeSetting(name = "Class", scope = "entity.name.class", foreground = snapshot.typeName),
    CodexTextMateThemeSetting(name = "Support Type", scope = "support.type", foreground = snapshot.typeName),
    CodexTextMateThemeSetting(name = "Support Class", scope = "support.class", foreground = snapshot.typeName),
    CodexTextMateThemeSetting(name = "Operator", scope = "keyword.operator", foreground = snapshot.operator),
    CodexTextMateThemeSetting(name = "Punctuation", scope = "punctuation", foreground = snapshot.punctuation),
    CodexTextMateThemeSetting(
      name = "Link",
      scope = "markup.underline.link",
      foreground = snapshot.hyperlink,
      fontStyle = "underline",
    ),
    CodexTextMateThemeSetting(name = "Heading", scope = "markup.heading", foreground = snapshot.heading),
    CodexTextMateThemeSetting(name = "Section", scope = "entity.name.section", foreground = snapshot.heading),
    CodexTextMateThemeSetting(name = "Tag", scope = "entity.name.tag", foreground = snapshot.status),
    CodexTextMateThemeSetting(name = "Status", scope = "meta.status", foreground = snapshot.status),
    CodexTextMateThemeSetting(name = "Status Line", scope = "meta.status-line", foreground = snapshot.status),
    CodexTextMateThemeSetting(name = "Status Line", scope = "meta.statusline", foreground = snapshot.status),
    CodexTextMateThemeSetting(
      name = "Inserted",
      scope = "markup.inserted",
      foreground = snapshot.foreground,
      background = snapshot.insertedBackground,
    ),
    CodexTextMateThemeSetting(
      name = "Diff Inserted",
      scope = "diff.inserted",
      foreground = snapshot.foreground,
      background = snapshot.insertedBackground,
    ),
    CodexTextMateThemeSetting(
      name = "Deleted",
      scope = "markup.deleted",
      foreground = snapshot.foreground,
      background = snapshot.deletedBackground,
    ),
    CodexTextMateThemeSetting(
      name = "Diff Deleted",
      scope = "diff.deleted",
      foreground = snapshot.foreground,
      background = snapshot.deletedBackground,
    ),
  )
}

private fun StringBuilder.appendTextMateSetting(setting: CodexTextMateThemeSetting) {
  append("    <dict>\n")
  setting.name?.let { appendXmlKeyString("name", it, indent = "      ") }
  setting.scope?.let { appendXmlKeyString("scope", it, indent = "      ") }
  append("      <key>settings</key>\n")
  append("      <dict>\n")
  setting.foreground?.let { appendXmlKeyString("foreground", it, indent = "        ") }
  setting.background?.let { appendXmlKeyString("background", it, indent = "        ") }
  setting.selection?.let { appendXmlKeyString("selection", it, indent = "        ") }
  setting.fontStyle?.let { appendXmlKeyString("fontStyle", it, indent = "        ") }
  append("      </dict>\n")
  append("    </dict>\n")
}

private fun StringBuilder.appendXmlKeyString(key: String, value: String, indent: String) {
  append(indent).append("<key>").append(codexThemeXmlEscape(key)).append("</key>\n")
  append(indent).append("<string>").append(codexThemeXmlEscape(value)).append("</string>\n")
}

private fun defaultCodexThemeRootDirectory(): Path {
  return PathManager.getSystemDir().resolve("agent-workbench").resolve("codex-themes")
}

private fun currentCodexEditorThemeColors(): CodexEditorThemeColors {
  return EditorColorsManager.getInstance().globalScheme.toCodexEditorThemeColors()
}

// External names of ConsoleViewContentType.NORMAL_OUTPUT_KEY / CONSOLE_BACKGROUND_KEY. The terminal uses these for defaults,
// but the declaring class lives outside this module's direct dependencies.
private val CODEX_TERMINAL_FOREGROUND_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_NORMAL_OUTPUT")
private val CODEX_TERMINAL_BACKGROUND_KEY = ColorKey.createColorKey("CONSOLE_BACKGROUND_KEY")

internal fun EditorColorsScheme.toCodexEditorThemeColors(): CodexEditorThemeColors {
  return CodexEditorThemeColors(
    defaultForeground = defaultForeground,
    defaultBackground = defaultBackground,
    terminalForeground = foreground(CODEX_TERMINAL_FOREGROUND_KEY) ?: defaultForeground,
    terminalBackground = getColor(CODEX_TERMINAL_BACKGROUND_KEY) ?: defaultBackground,
    selectionBackground = getColor(EditorColors.SELECTION_BACKGROUND_COLOR),
    addedLines = getColor(EditorColors.ADDED_LINES_COLOR),
    deletedLines = getColor(EditorColors.DELETED_LINES_COLOR),
    hyperlink = foreground(CodeInsightColors.HYPERLINK_ATTRIBUTES) ?: foreground(EditorColors.REFERENCE_HYPERLINK_COLOR),
    comment = foreground(DefaultLanguageHighlighterColors.LINE_COMMENT) ?: foreground(DefaultLanguageHighlighterColors.BLOCK_COMMENT),
    keyword = foreground(DefaultLanguageHighlighterColors.KEYWORD),
    function = foreground(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION) ?: foreground(DefaultLanguageHighlighterColors.FUNCTION_CALL),
    variable = foreground(DefaultLanguageHighlighterColors.LOCAL_VARIABLE) ?: foreground(DefaultLanguageHighlighterColors.IDENTIFIER),
    stringLiteral = foreground(DefaultLanguageHighlighterColors.STRING),
    numberLiteral = foreground(DefaultLanguageHighlighterColors.NUMBER),
    typeName = foreground(DefaultLanguageHighlighterColors.CLASS_NAME) ?: foreground(DefaultLanguageHighlighterColors.CLASS_REFERENCE),
    operator = foreground(DefaultLanguageHighlighterColors.OPERATION_SIGN),
    punctuation = foreground(DefaultLanguageHighlighterColors.COMMA) ?: foreground(DefaultLanguageHighlighterColors.DOT),
  )
}

private fun EditorColorsScheme.foreground(key: TextAttributesKey): Color? = getAttributes(key)?.foregroundColor

internal fun codexThemeTomlString(value: String): String {
  return buildString {
    append('"')
    for (char in value) {
      when (char) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\b' -> append("\\b")
        '\t' -> append("\\t")
        '\n' -> append("\\n")
        '\u000C' -> append("\\f")
        '\r' -> append("\\r")
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
}

private fun codexThemeXmlEscape(value: String): String {
  return buildString {
    for (char in value) {
      when (char) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(char)
      }
    }
  }
}

private fun buildManifest(fileName: String, hash: String): String {
  return buildString {
    append("formatVersion=")
    append(CODEX_THEME_MATERIALIZATION_FORMAT_VERSION)
    append('\n')
    append(fileName)
    append('=')
    append(hash)
    append('\n')
  }
}

private fun readManagedThemeFileNames(manifest: String?): Set<String> {
  if (manifest == null) return emptySet()
  if (!manifest.startsWith("formatVersion=$CODEX_THEME_MATERIALIZATION_FORMAT_VERSION\n")) return emptySet()
  return manifest.lineSequence()
    .drop(1)
    .mapNotNull { line ->
      val separatorIndex = line.indexOf('=')
      if (separatorIndex <= 0) return@mapNotNull null
      line.substring(0, separatorIndex).takeIf(::isManagedThemeFileName)
    }
    .toCollection(LinkedHashSet())
}

private fun deleteStaleManagedThemeFiles(rootDirectory: Path, previousManagedFileNames: Set<String>, currentFileName: String) {
  for (fileName in previousManagedFileNames) {
    if (fileName == currentFileName) continue
    val path = rootDirectory.resolve(fileName)
    if (Files.isRegularFile(path)) {
      Files.deleteIfExists(path)
    }
  }
}

private fun isManagedThemeFileName(fileName: String): Boolean {
  return CODEX_THEME_MANAGED_FILE_REGEX.matches(fileName) && '/' !in fileName && '\\' !in fileName
}

private fun sha256HexOrNull(path: Path): String? {
  if (!Files.isRegularFile(path)) return null
  return runCatching {
    val digest = DigestUtil.sha256()
    DigestUtil.updateContentHash(digest, path)
    DigestUtil.digestToHash(digest)
  }.getOrNull()
}

private fun readStringOrNull(path: Path): String? {
  if (!Files.isRegularFile(path)) return null
  return runCatching { EelFiles.readString(path) }.getOrNull()
}

private fun writeStringIfChanged(path: Path, content: String) {
  if (readStringOrNull(path) == content) return
  writeAtomically(path, content.toByteArray(StandardCharsets.UTF_8))
}

private fun writeAtomically(path: Path, bytes: ByteArray) {
  val parent = path.parent ?: error("Materialized Codex theme path must have a parent: $path")
  Files.createDirectories(parent)
  val tempFile = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
  var moved = false
  try {
    Files.write(tempFile, bytes)
    try {
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
    }
    moved = true
  }
  finally {
    if (!moved) {
      Files.deleteIfExists(tempFile)
    }
  }
}

private fun Color?.toCodexThemeHexOrFallback(fallback: String, background: Color): String {
  return (this ?: fallback.toColor()).toCodexThemeHex(background)
}

private fun Color.toCodexThemeHex(background: Color): String {
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

private val CODEX_DARK_FALLBACK = CodexThemeFallbackPalette(
  accent = "#548AF7",
  success = "#6AAB73",
  error = "#DB5C5C",
  heading = "#C9A26D",
  syntaxComment = "#7A7E85",
  syntaxKeyword = "#CF8E6D",
  syntaxFunction = "#56A8F5",
  syntaxVariable = "#DFE1E5",
  syntaxString = "#6AAB73",
  syntaxNumber = "#2AACB8",
  syntaxType = "#B3AE60",
  syntaxOperator = "#B0B7C3",
  syntaxPunctuation = "#B0B7C3",
)

private val CODEX_LIGHT_FALLBACK = CodexThemeFallbackPalette(
  accent = "#3574F0",
  success = "#208A3C",
  error = "#C7222D",
  heading = "#8F6500",
  syntaxComment = "#8C8C8C",
  syntaxKeyword = "#0033B3",
  syntaxFunction = "#00627A",
  syntaxVariable = "#1F2328",
  syntaxString = "#067D17",
  syntaxNumber = "#1750EB",
  syntaxType = "#2E7D32",
  syntaxOperator = "#1F2328",
  syntaxPunctuation = "#5F6570",
)

private const val CODEX_THEME_FILE_NAME_PREFIX: String = "agent-workbench-idea-"
private const val CODEX_THEME_FILE_EXTENSION: String = ".tmTheme"
private const val CODEX_THEME_FILE_NAME_HASH_PREFIX_LENGTH: Int = 16
private const val CODEX_THEME_MANIFEST_FILE_NAME: String = ".awb-codex-theme-manifest"
private const val CODEX_THEME_MATERIALIZATION_FORMAT_VERSION: Int = 1
private val CODEX_THEME_MANAGED_FILE_REGEX = Regex("$CODEX_THEME_FILE_NAME_PREFIX[0-9a-f]{16}\\$CODEX_THEME_FILE_EXTENSION")
