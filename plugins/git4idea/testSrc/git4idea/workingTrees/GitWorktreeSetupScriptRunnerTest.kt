// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
internal class GitWorktreeSetupScriptRunnerTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test runs the script once with the worktree directory as the working directory and sole argument`(): Unit = with(context) {
    val worktreeDir = testNioRoot.resolve("worktree")
    Files.createDirectories(worktreeDir)
    val outputFile = testNioRoot.resolve("setup-script-output.txt")
    val script = writeExecutableScript(
      posixContent = """
        #!/bin/sh
        pwd > "$outputFile"
        echo "$1" >> "$outputFile"
      """.trimIndent(),
      windowsContent = """
        @echo off
        cd > "$outputFile"
        echo %1>>"$outputFile"
      """.trimIndent(),
    )

    timeoutRunBlocking {
      GitWorktreeSetupScriptRunner.runSetupScript(project, script, worktreeDir)
    }

    val lines = Files.readAllLines(outputFile)
    assertThat(lines[0]).isEqualTo(worktreeDir.toRealPath().toString())
    assertThat(lines[1]).isEqualTo(worktreeDir.toString())
  }

  @Test
  fun `test does not throw when the script does not exist`(): Unit = with(context) {
    val worktreeDir = testNioRoot.resolve("worktree")
    Files.createDirectories(worktreeDir)

    timeoutRunBlocking {
      GitWorktreeSetupScriptRunner.runSetupScript(project, testNioRoot.resolve("no-such-script"), worktreeDir)
    }
  }

  private fun writeExecutableScript(posixContent: String, windowsContent: String): Path {
    val suffix = if (SystemInfo.isWindows) ".cmd" else ".sh"
    val content = if (SystemInfo.isWindows) windowsContent else posixContent
    return ExecUtil.createTempExecutableScript("setup-script", suffix, content).toPath()
  }
}
