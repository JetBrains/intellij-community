// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.util.io.DigestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiThemeSupportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun materializesBundledExtensionManifestAndCurrentThemeState() {
    val support = supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
      themeSnapshotProvider = { snapshot("islands-dark", "Islands Dark", dark = true) },
    )

    val resources = support.launchResourcesOrNull()

    assertThat(resources).isEqualTo(
      PiThemeLaunchResources(
        extensionPath = tempDir.resolve("extension").resolve("agent-workbench-theme.ts"),
        stateFilePath = tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(Files.readString(resources!!.extensionPath)).isEqualTo("extension-v1")
    assertThat(Files.readString(resources.stateFilePath))
      .contains("\"themeId\":\"islands-dark\"", "\"dark\":true")
    assertThat(tempDir.resolve(".awb-theme-manifest")).exists()
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .isEqualTo(
        """
          formatVersion=3
          agent-workbench-theme.ts=${DigestUtil.sha256Hex(bytes("extension-v1"))}
        """.trimIndent() + "\n"
      )
  }

  @Test
  fun rewritesStaleMaterializedExtensionWhenBundledHashChanges() {
    supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
    ).launchResourcesOrNull()

    supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v2")),
    ).launchResourcesOrNull()

    assertThat(Files.readString(tempDir.resolve("extension").resolve("agent-workbench-theme.ts"))).isEqualTo("extension-v2")
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .contains("agent-workbench-theme.ts=${DigestUtil.sha256Hex(bytes("extension-v2"))}")
  }

  @Test
  fun removesUnexpectedFilesFromManagedExtensionDirectory() {
    val extensionDirectory = tempDir.resolve("extension")
    Files.createDirectories(extensionDirectory)
    Files.writeString(extensionDirectory.resolve("old.ts"), "stale")

    supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
    ).launchResourcesOrNull()

    assertThat(extensionDirectory.resolve("old.ts")).doesNotExist()
    assertThat(extensionDirectory.resolve("agent-workbench-theme.ts")).exists()
  }

  @Test
  fun removesLegacyThemeFilesWhenManifestFormatChanges() {
    val legacyThemeDirectory = tempDir.resolve("themes")
    Files.createDirectories(legacyThemeDirectory)
    Files.writeString(legacyThemeDirectory.resolve("dark.json"), "stale")
    Files.writeString(tempDir.resolve(".awb-theme-manifest"), "formatVersion=1\ndark.json=stale\n")

    supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
    ).launchResourcesOrNull()

    assertThat(legacyThemeDirectory.resolve("dark.json")).doesNotExist()
  }

  @Test
  fun cachesMaterializedExtensionAfterFirstSuccessAndRefreshesThemeState() {
    var loadCount = 0
    var themeId = "islands-dark"
    val support = PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = {
        loadCount++
        PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1"))
      },
      themeSnapshotProvider = { snapshot(themeId, themeId, dark = themeId != "islands-light") },
    )

    val first = support.launchResourcesOrNull()
    themeId = "islands-light"
    val second = support.launchResourcesOrNull()

    assertThat(first).isEqualTo(
      PiThemeLaunchResources(
        tempDir.resolve("extension").resolve("agent-workbench-theme.ts"),
        tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(second).isEqualTo(first)
    assertThat(loadCount).isEqualTo(1)
    assertThat(Files.readString(second!!.stateFilePath)).contains("\"themeId\":\"islands-light\"")
  }

  @Test
  fun syncCurrentThemeStateUsesMaterializedStateFile() {
    var themeId = "islands-dark"
    val support = supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
      themeSnapshotProvider = { snapshot(themeId, themeId, dark = themeId != "islands-light") },
    )
    val resources = support.launchResourcesOrNull()

    themeId = "islands-light"
    support.syncCurrentThemeState()

    assertThat(Files.readString(resources!!.stateFilePath)).contains("\"themeId\":\"islands-light\"")
  }

  @Test
  fun returnsNullWhenMaterializationFails() {
    val support = PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = { error("missing extension") },
    )

    assertThat(support.launchResourcesOrNull()).isNull()
  }

  private fun supportFor(
    extension: PiBundledThemeExtensionResource,
    themeSnapshotProvider: () -> PiThemeSnapshot = { snapshot("islands-dark", "Islands Dark", dark = true) },
  ): PiThemeSupport {
    return PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = { extension },
      themeSnapshotProvider = themeSnapshotProvider,
    )
  }

  private fun bytes(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)
}

private fun snapshot(themeId: String, themeName: String, dark: Boolean): PiThemeSnapshot {
  return PiThemeSnapshot(
    formatVersion = PI_THEME_STATE_FORMAT_VERSION,
    themeId = themeId,
    themeName = themeName,
    dark = dark,
    fg = PI_THEME_TEST_FG_KEYS.associateWith { "#111111" },
    bg = PI_THEME_TEST_BG_KEYS.associateWith { "#222222" },
  )
}

internal val PI_THEME_TEST_FG_KEYS = listOf(
  "accent",
  "border",
  "borderAccent",
  "borderMuted",
  "success",
  "error",
  "warning",
  "muted",
  "dim",
  "text",
  "thinkingText",
  "userMessageText",
  "customMessageText",
  "customMessageLabel",
  "toolTitle",
  "toolOutput",
  "mdHeading",
  "mdLink",
  "mdLinkUrl",
  "mdCode",
  "mdCodeBlock",
  "mdCodeBlockBorder",
  "mdQuote",
  "mdQuoteBorder",
  "mdHr",
  "mdListBullet",
  "toolDiffAdded",
  "toolDiffRemoved",
  "toolDiffContext",
  "syntaxComment",
  "syntaxKeyword",
  "syntaxFunction",
  "syntaxVariable",
  "syntaxString",
  "syntaxNumber",
  "syntaxType",
  "syntaxOperator",
  "syntaxPunctuation",
  "thinkingOff",
  "thinkingMinimal",
  "thinkingLow",
  "thinkingMedium",
  "thinkingHigh",
  "thinkingXhigh",
  "bashMode",
)

internal val PI_THEME_TEST_BG_KEYS = listOf(
  "selectedBg",
  "userMessageBg",
  "customMessageBg",
  "toolPendingBg",
  "toolSuccessBg",
  "toolErrorBg",
)
