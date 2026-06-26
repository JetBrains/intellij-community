// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexThemeSupportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun rendersBaseColorsAndRequiredScopes() {
    val xml = testSnapshot().toTmThemeXml()

    assertThat(xml).contains(
      "<key>foreground</key>\n        <string>#010203</string>",
      "<key>background</key>\n        <string>#111213</string>",
      "<key>selection</key>\n        <string>#212223</string>",
      "<key>scope</key>\n      <string>markup.inserted</string>",
      "<key>background</key>\n        <string>#0A3B1C</string>",
      "<key>scope</key>\n      <string>markup.deleted</string>",
      "<key>background</key>\n        <string>#4A1E1E</string>",
    )
    for (scope in REQUIRED_TEXTMATE_SCOPES) {
      assertThat(xml).contains("<string>$scope</string>")
    }
  }

  @Test
  fun snapshotBuilderUsesEditorColorsBeforeFallbackPalette() {
    val snapshot = CodexThemeSnapshotBuilder(
      editorThemeProvider = { editorThemeColors() },
    ).buildSnapshot()

    assertThat(snapshot.foreground).isEqualTo("#010203")
    assertThat(snapshot.background).isEqualTo("#111213")
    assertThat(snapshot.selection).isEqualTo("#212223")
    assertThat(snapshot.insertedBackground).isEqualTo("#0A3B1C")
    assertThat(snapshot.deletedBackground).isEqualTo("#4A1E1E")
    assertThat(snapshot.hyperlink).isEqualTo("#2244DD")
    assertThat(snapshot.comment).isEqualTo("#778899")
    assertThat(snapshot.keyword).isEqualTo("#AA5500")
    assertThat(snapshot.function).isEqualTo("#3366AA")
    assertThat(snapshot.variable).isEqualTo("#D0D0D0")
    assertThat(snapshot.stringLiteral).isEqualTo("#339955")
    assertThat(snapshot.numberLiteral).isEqualTo("#2299AA")
    assertThat(snapshot.typeName).isEqualTo("#BBAA55")
    assertThat(snapshot.operator).isEqualTo("#C0C0C0")
    assertThat(snapshot.punctuation).isEqualTo("#A0A0A0")
  }

  @Test
  fun materializesThemeUnderInjectedRootAndReturnsConfigWithoutExtension() {
    val support = supportFor(testSnapshot())

    val config = checkNotNull(support.launchConfigOrNull())

    val rootDirectory = tempDir.toAbsolutePath().normalize()
    assertThat(config.themeFilePath.parent).isEqualTo(rootDirectory)
    assertThat(config.themeFilePath.fileName.toString())
      .startsWith("agent-workbench-idea-")
      .endsWith(".tmTheme")
    assertThat(Files.readString(config.themeFilePath)).contains("#010203", "markup.inserted", "markup.deleted")
    assertThat(config.themeConfigValue).isEqualTo(
      "tui.theme=${codexThemeTomlString(config.themeFilePath.toString().removeSuffix(".tmTheme"))}"
    )
    assertThat(config.themeConfigValue).doesNotContain(".tmTheme")
  }

  @Test
  fun skipsWritingThemeWhenContentIsUnchanged() {
    val support = supportFor(testSnapshot())
    val first = checkNotNull(support.launchConfigOrNull())
    val modifiedTime = Files.getLastModifiedTime(first.themeFilePath)

    val second = checkNotNull(support.launchConfigOrNull())

    assertThat(second).isEqualTo(first)
    assertThat(Files.getLastModifiedTime(first.themeFilePath)).isEqualTo(modifiedTime)
  }

  @Test
  fun themeFileNameChangesWhenContentChanges() {
    var snapshot = testSnapshot(foreground = "#010203")
    val support = CodexThemeSupport(
      rootDirectoryProvider = { tempDir },
      themeSnapshotProvider = { snapshot },
    )
    val first = checkNotNull(support.launchConfigOrNull())

    snapshot = testSnapshot(foreground = "#0B0C0D")
    val second = checkNotNull(support.launchConfigOrNull())

    assertThat(second.themeFilePath.fileName.toString()).isNotEqualTo(first.themeFilePath.fileName.toString())
  }

  @Test
  fun tomlStringEscapesSpecialAndControlCharacters() {
    assertThat(codexThemeTomlString("a\"b\\c\n\r\t\b\u000C\u0001"))
      .isEqualTo("\"a\\\"b\\\\c\\n\\r\\t\\b\\f\\u0001\"")
  }

  @Test
  fun manifestCleanupDeletesOnlyPreviouslyManagedThemeFiles() {
    Files.writeString(tempDir.resolve(".awb-codex-theme-manifest"), """
      formatVersion=1
      agent-workbench-idea-1111111111111111.tmTheme=old
      manual.tmTheme=manual
      agent-workbench-idea-notmanaged.tmTheme=invalid
    """.trimIndent() + "\n")
    val recordedManagedFile = tempDir.resolve("agent-workbench-idea-1111111111111111.tmTheme")
    val unrecordedManagedFile = tempDir.resolve("agent-workbench-idea-2222222222222222.tmTheme")
    val unrelatedFile = tempDir.resolve("manual.tmTheme")
    val invalidRecordedFile = tempDir.resolve("agent-workbench-idea-notmanaged.tmTheme")
    Files.writeString(recordedManagedFile, "old")
    Files.writeString(unrecordedManagedFile, "user")
    Files.writeString(unrelatedFile, "user")
    Files.writeString(invalidRecordedFile, "user")

    val config = checkNotNull(supportFor(testSnapshot()).launchConfigOrNull())

    assertThat(recordedManagedFile).doesNotExist()
    assertThat(unrecordedManagedFile).exists()
    assertThat(unrelatedFile).exists()
    assertThat(invalidRecordedFile).exists()
    assertThat(config.themeFilePath).exists()
    assertThat(Files.readString(tempDir.resolve(".awb-codex-theme-manifest")))
      .contains(config.themeFilePath.fileName.toString())
      .doesNotContain("manual.tmTheme")
  }

  @Test
  fun returnsNullWhenThemeMaterializationFails() {
    val support = CodexThemeSupport(
      rootDirectoryProvider = { tempDir },
      themeSnapshotProvider = { error("theme unavailable") },
    )

    assertThat(support.launchConfigOrNull()).isNull()
  }

  private fun supportFor(snapshot: CodexThemeSnapshot): CodexThemeSupport {
    return CodexThemeSupport(
      rootDirectoryProvider = { tempDir },
      themeSnapshotProvider = { snapshot },
    )
  }
}

private fun testSnapshot(
  foreground: String = "#010203",
  background: String = "#111213",
): CodexThemeSnapshot {
  return CodexThemeSnapshot(
    foreground = foreground,
    background = background,
    selection = "#212223",
    comment = "#778899",
    keyword = "#AA5500",
    function = "#3366AA",
    variable = "#D0D0D0",
    stringLiteral = "#339955",
    numberLiteral = "#2299AA",
    typeName = "#BBAA55",
    operator = "#C0C0C0",
    punctuation = "#A0A0A0",
    hyperlink = "#2244DD",
    heading = "#D6A13A",
    status = "#44AAEE",
    insertedBackground = "#0A3B1C",
    deletedBackground = "#4A1E1E",
  )
}

private fun editorThemeColors(): CodexEditorThemeColors {
  return CodexEditorThemeColors(
    defaultForeground = Color(0xFAFAFA),
    defaultBackground = Color(0x202124),
    terminalForeground = Color(0x010203),
    terminalBackground = Color(0x111213),
    selectionBackground = Color(0x212223),
    addedLines = Color(0x0A3B1C),
    deletedLines = Color(0x4A1E1E),
    hyperlink = Color(0x2244DD),
    comment = Color(0x778899),
    keyword = Color(0xAA5500),
    function = Color(0x3366AA),
    variable = Color(0xD0D0D0),
    stringLiteral = Color(0x339955),
    numberLiteral = Color(0x2299AA),
    typeName = Color(0xBBAA55),
    operator = Color(0xC0C0C0),
    punctuation = Color(0xA0A0A0),
  )
}

private val REQUIRED_TEXTMATE_SCOPES: List<String> = listOf(
  "comment",
  "keyword",
  "keyword.control",
  "storage.type",
  "storage.modifier",
  "entity.name.function",
  "support.function",
  "variable",
  "variable.parameter",
  "string",
  "constant",
  "constant.numeric",
  "constant.language",
  "constant.other",
  "entity.name.type",
  "entity.name.class",
  "support.type",
  "support.class",
  "keyword.operator",
  "punctuation",
  "markup.underline.link",
  "markup.heading",
  "entity.name.section",
  "entity.name.tag",
  "meta.status",
  "meta.status-line",
  "meta.statusline",
  "markup.inserted",
  "diff.inserted",
  "markup.deleted",
  "diff.deleted",
)
