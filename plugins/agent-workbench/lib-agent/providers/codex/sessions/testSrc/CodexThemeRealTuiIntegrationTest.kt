// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexWebSocketAppServerClient
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
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
        responsePlans = listOf(
          MockResponsesPlan.completedApplyPatch(THEME_DIFF_PATCH),
          MockResponsesPlan.completedAssistantMessage(THEME_SMOKE_RESPONSE),
        ),
      ).use { harness ->
        val client = CodexWebSocketAppServerClient(
          coroutineScope = this,
          executablePathProvider = { codexBinary },
          environmentOverrides = mapOf("CODEX_HOME" to harness.codexHome.toString()),
          workingDirectory = harness.projectDir,
        )
        try {
          val thread = client.createThreadSession(
            cwd = harness.projectDir.toString(),
            approvalPolicy = "never",
            sandbox = "workspace-write",
          ).thread
          client.materializeThread(thread.id)
          val remoteUrl = client.currentRemoteUrl()
          assertThat(harness.requests()).isEmpty()

          harness.startRemoteResume(
            remoteUrl = remoteUrl,
            threadId = thread.id,
            extraConfigArgs = listOf(CODEX_TERMINAL_TITLE_CONFIG, themeLaunchConfig.themeConfigValue),
          ).use { session ->
            assertThat(session.awaitTerminalThreadId()).isEqualTo(thread.id.lowercase())
            assertThat(harness.requests()).isEmpty()
            client.startTurn(threadId = thread.id, text = THEME_SMOKE_PROMPT)
            val request = eventually(timeout = 20.seconds) {
              harness.requests()
                .firstOrNull { request -> request.contains(THEME_SMOKE_PROMPT) }
            } ?: error("Timed out waiting for Codex app-server request with generated theme config.\n${session.diagnostics()}")

            assertThat(request).contains(THEME_SMOKE_PROMPT)
            eventually(timeout = 20.seconds) {
              harness.requests().takeIf { requests -> requests.size >= 2 }
            } ?: error("Timed out waiting for Codex app-server apply_patch follow-up request.\n${session.diagnostics()}")
            session.awaitOutputContains(THEME_SMOKE_RESPONSE_INTRO)
            session.awaitOutputContains(THEME_SMOKE_CODE_IDENTIFIER)
            session.awaitRawOutputMatches(THEME_KEYWORD_FOREGROUND_ANSI_REGEX)
            session.awaitRawOutputMatches(THEME_INSERTED_BACKGROUND_ANSI_REGEX)
            assertThat(Files.readString(harness.projectDir.resolve(THEME_DIFF_FILE)))
              .isEqualTo("def themed_diff():\n    return \"diff\"\n")
          }
        }
        finally {
          client.shutdown()
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
private const val CODEX_TERMINAL_TITLE_CONFIG: String = "tui.terminal_title=[\"thread-id\",\"thread\"]"
private const val THEME_DIFF_FILE: String = "theme_diff.py"
private const val THEME_DIFF_PATCH: String = """*** Begin Patch
*** Add File: theme_diff.py
+def themed_diff():
+    return "diff"
*** End Patch"""
private const val THEME_SMOKE_RESPONSE_INTRO: String = "Theme smoke response"
private const val THEME_SMOKE_RESPONSE: String = """$THEME_SMOKE_RESPONSE_INTRO

```python
def themed_function():
    return "theme"
```"""
private const val THEME_SMOKE_CODE_IDENTIFIER: String = "themed_function"
private val THEME_KEYWORD_FOREGROUND_ANSI_REGEX = Regex("\u001B\\[[0-9;]*38;2;170;85;0[0-9;]*m")
private val THEME_INSERTED_BACKGROUND_ANSI_REGEX = Regex("\u001B\\[[0-9;]*48;2;10;59;28[0-9;]*m")
