// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexThemeRealTuiIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun realTuiStartsWithMaterializedAbsoluteThemeConfig() {
    runBlocking(Dispatchers.IO) {
      val codexBinary = requireRealCodexBinary()
      val themeLaunchConfig = checkNotNull(CodexThemeSupport(
        rootDirectoryProvider = { tempDir.resolve("themes") },
        themeSnapshotProvider = { realTuiThemeSnapshot() },
      ).launchConfigOrNull())
      assertThat(themeLaunchConfig.themeFilePath).isRegularFile()
      assertThat(themeLaunchConfig.themeConfigValue)
        .startsWith("tui.theme=")
        .doesNotContain(".tmTheme")

      CodexRealTuiHarness(
        codexBinary = codexBinary,
        tempRoot = tempDir.resolve("theme-launch"),
        responsePlans = listOf(MockResponsesPlan.completedAssistantMessage(THEME_SMOKE_RESPONSE)),
      ).use { harness ->
        harness.start(
          prompt = THEME_SMOKE_PROMPT,
          extraConfigArgs = listOf(themeLaunchConfig.themeConfigValue),
        ).use { session ->
          val request = eventually(timeout = 20.seconds) {
            harness.requests()
              .takeIf { requests -> requests.size == 1 }
              ?.singleOrNull { request -> request.contains(THEME_SMOKE_PROMPT) }
          } ?: error("Timed out waiting for Codex TUI request with generated theme config.\n${session.diagnostics()}")

          assertThat(request).contains(THEME_SMOKE_PROMPT)
          session.awaitOutputContains(THEME_SMOKE_RESPONSE)
        }
      }
    }
  }

  private fun requireRealCodexBinary(): String {
    assumeTrue(CodexRealTuiHarness.isSupportedPlatform(), "Real Codex TUI theme test is supported on macOS/Linux only.")
    val codexBinary = CodexRealTuiHarness.resolveCodexBinary()
    assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")
    return codexBinary!!
  }
}

private fun realTuiThemeSnapshot(): CodexThemeSnapshot {
  return CodexThemeSnapshot(
    foreground = "#010203",
    background = "#111213",
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

private const val THEME_SMOKE_PROMPT: String = "Reply once for the generated Codex theme smoke test."
private const val THEME_SMOKE_RESPONSE: String = "Theme smoke response"
