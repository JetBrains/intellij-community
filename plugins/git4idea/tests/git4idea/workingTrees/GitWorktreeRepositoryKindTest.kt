// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.gitPlatformContextFixture
import git4idea.workingTrees.ui.GitRepositoryHeader
import git4idea.workingTrees.ui.GitRepositoryKind
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Classification of repositories in the Worktrees tab: a plain repository nested in another repository's
 * working directory is marked NESTED, while the container stays TOP_LEVEL.
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreeRepositoryKindTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @Test
  fun `test a plain nested repository is classified as nested and the container as top-level`(): Unit = with(context) {
    val top = createRepository(project, projectNioRoot, true)
    val nested = createRepository(project, projectNioRoot.resolve("nested"), true)
    GitRepositoriesHolder.getAndInit(project)

    val headers = GitWorktreesUiUtil.buildEntries(project)
      .filterIsInstance<GitRepositoryHeader>()
      .associateBy { it.repository.root.path }

    assertThat(headers[nested.root.path]?.kind).isEqualTo(GitRepositoryKind.NESTED)
    assertThat(headers[top.root.path]?.kind).isEqualTo(GitRepositoryKind.TOP_LEVEL)
  }
}
