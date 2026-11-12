// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Runs [GitWorktreeSetupScriptRunner] with the script and the worktree directory both backed by an Eel
 * filesystem, not the JVM default one. Checks that the worktree directory resolves correctly in the
 * script's own environment, both as its working directory and as its sole command-line argument.
 *
 * Every leg this API can produce (local, WSL, Docker) presents a POSIX shell, except a local leg on a
 * Windows host, which this test skips: Windows batch-script coverage lives in [GitWorktreeSetupScriptRunnerTest].
 * A docker leg only runs when a docker daemon is available; it is never required.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
@DockerTest(image = "alpine/git", mandatory = false)
internal class GitWorktreeSetupScriptRunnerEelTest(@Suppress("unused") val eelHolder: EelHolder) {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test runs the script with the eel-resolved worktree directory as the working directory and sole argument`(): Unit = with(context) {
    assumeFalse(eelHolder.eel.platform.isWindows, "Windows batch-script coverage lives in GitWorktreeSetupScriptRunnerTest")

    val worktreeDir = testNioRoot.resolve("worktree")
    Files.createDirectories(worktreeDir)
    val outputFile = testNioRoot.resolve("setup-script-output.txt")
    val eelOutputFile = outputFile.asEelPath()
    val script = testNioRoot.resolve("setup-script.sh")
    Files.writeString(
      script,
      """
        #!/bin/sh
        pwd > "$eelOutputFile"
        echo "$1" >> "$eelOutputFile"
      """.trimIndent(),
    )
    Files.setPosixFilePermissions(
      script,
      setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
    )

    val succeeded = runBlocking {
      GitWorktreeSetupScriptRunner.runSetupScript(project, script, worktreeDir)
    }

    assertThat(succeeded).isTrue()
    val lines = Files.readAllLines(outputFile)
    assertThat(lines[0]).isEqualTo(worktreeDir.toRealPath().asEelPath().toString())
    assertThat(lines[1]).isEqualTo(worktreeDir.asEelPath().toString())
  }
}
