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
      themeModeProvider = { PiThemeMode.DARK },
    )

    val resources = support.launchResourcesOrNull()

    assertThat(resources).isEqualTo(
      PiThemeLaunchResources(
        extensionPath = tempDir.resolve("extension").resolve("agent-workbench-theme.ts"),
        stateFilePath = tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(Files.readString(resources!!.extensionPath)).isEqualTo("extension-v1")
    assertThat(Files.readString(resources.stateFilePath)).isEqualTo("dark\n")
    assertThat(tempDir.resolve(".awb-theme-manifest")).exists()
    assertThat(Files.readString(tempDir.resolve(".awb-theme-manifest")))
      .isEqualTo(
        """
          formatVersion=2
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
    var themeMode = PiThemeMode.DARK
    val support = PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = {
        loadCount++
        PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1"))
      },
      themeModeProvider = { themeMode },
    )

    val first = support.launchResourcesOrNull()
    themeMode = PiThemeMode.LIGHT
    val second = support.launchResourcesOrNull()

    assertThat(first).isEqualTo(
      PiThemeLaunchResources(
        tempDir.resolve("extension").resolve("agent-workbench-theme.ts"),
        tempDir.resolve("state").resolve("current-theme.txt"),
      )
    )
    assertThat(second).isEqualTo(first)
    assertThat(loadCount).isEqualTo(1)
    assertThat(Files.readString(second!!.stateFilePath)).isEqualTo("light\n")
  }

  @Test
  fun syncCurrentThemeStateUsesMaterializedStateFile() {
    var themeMode = PiThemeMode.DARK
    val support = supportFor(
      PiBundledThemeExtensionResource("agent-workbench-theme.ts", bytes("extension-v1")),
      themeModeProvider = { themeMode },
    )
    val resources = support.launchResourcesOrNull()

    themeMode = PiThemeMode.LIGHT
    support.syncCurrentThemeState()

    assertThat(Files.readString(resources!!.stateFilePath)).isEqualTo("light\n")
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
    themeModeProvider: () -> PiThemeMode = { PiThemeMode.DARK },
  ): PiThemeSupport {
    return PiThemeSupport(
      rootDirectoryProvider = { tempDir },
      extensionResourceProvider = { extension },
      themeModeProvider = themeModeProvider,
    )
  }

  private fun bytes(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)
}
