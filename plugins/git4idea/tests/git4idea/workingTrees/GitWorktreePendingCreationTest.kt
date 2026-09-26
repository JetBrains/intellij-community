// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.repo.repositoryId
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcsUtil.VcsUtil
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.gitPlatformContextFixture
import git4idea.workingTrees.ui.GitWorktreeCreatingRow
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A worktree creation still in flight (its target directory has no real [git4idea.GitWorkingTree] yet) must appear
 * in the Worktrees tab immediately as a [GitWorktreeCreatingRow], instead of only after `git worktree add` finishes.
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreePendingCreationTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @Test
  fun `test a pending worktree creation appears as a creating row`(): Unit = with(context) {
    val repository = createRepository(project, projectNioRoot, true)
    GitRepositoriesHolder.getAndInit(project)

    val targetPath = VcsUtil.getFilePath(projectNioRoot.resolve("new-worktree"), true)
    val pending = GitWorktreePendingCreation(
      repositoryId = repository.repositoryId(),
      targetPath = targetPath,
      presentableBranchName = "feature",
    )

    val creatingRows = GitWorktreesUiUtil.buildEntries(project, mapOf(targetPath to pending)).filterIsInstance<GitWorktreeCreatingRow>()

    assertThat(creatingRows).hasSize(1)
    assertThat(creatingRows.single().presentableBranchName).isEqualTo("feature")
    assertThat(creatingRows.single().targetPath).isEqualTo(targetPath)
  }

  @Test
  fun `test no pending creations means no creating rows`(): Unit = with(context) {
    createRepository(project, projectNioRoot, true)
    GitRepositoriesHolder.getAndInit(project)

    val creatingRows = GitWorktreesUiUtil.buildEntries(project).filterIsInstance<GitWorktreeCreatingRow>()

    assertThat(creatingRows).isEmpty()
  }
}
