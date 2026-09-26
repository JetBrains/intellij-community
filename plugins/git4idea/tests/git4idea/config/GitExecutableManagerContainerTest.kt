// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import git4idea.commands.Git
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass

/**
 * IJPL-253295: a saved application-wide "Path to Git executable" names a file on the IDE's own
 * machine. When a project runs on another machine through Eel, for example a Docker dev
 * container, [GitExecutableManager] must not hand that stale host path to git commands, or every
 * one of them fails and idea.log fills with repeated worktree-listing WARNs.
 */
@ParameterizedClass
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@WslTest(mandatory = false)
@DockerTest(image = "alpine/git", mandatory = false)
internal class GitExecutableManagerContainerTest(private val eelHolder: EelHolder) {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `git worktree list succeeds with a stale host-only application git path`() {
    val staleHostPath = "/opt/homebrew/bin/git"
    GitVcsApplicationSettings.getInstance().setPathToGit(staleHostPath)

    val executable = GitExecutableManager.getInstance().getExecutable(context.project, context.projectNioRoot)

    if (eelHolder.type == EelType.Local) {
      assertEquals(staleHostPath, executable.exePath, "On the IDE's own machine, the saved path must still apply")
      return
    }

    assertNotEquals(staleHostPath, executable.exePath,
                     "A directory on a ${eelHolder.type} machine must not use a git path saved for the IDE's own machine")
    assertDoesNotThrow(
      { Git.getInstance().listWorktrees(context.repo) },
      "`git worktree list` must succeed on a ${eelHolder.type} machine even with a stale host-only application path saved"
    )
  }
}
