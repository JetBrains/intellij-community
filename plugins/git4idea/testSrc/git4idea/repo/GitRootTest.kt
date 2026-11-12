// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitRootTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test non-existing dir`(): Unit = with(context) {
    val childDir = projectNioRoot.resolve("child")
    assertThat(GitUtil.findGitDir(childDir)).isNull()
  }

  @Test
  fun `test not git repo`(): Unit = with(context) {
    assertThat(GitUtil.findGitDir(testNioRoot)).isNull()
  }

  @Test
  fun `test simple repo`(): Unit = with(context) {
    assertThat(GitUtil.findGitDir(repo.root.toNioPath())).isEqualTo(projectNioRoot.resolve(".git"))
  }

  @Test
  fun `test git worktree`(): Unit = with(context) {
    val worktree = "test"
    git("worktree add $worktree")

    assertThat(GitUtil.findGitDir(projectNioRoot.resolve(worktree)))
      .isEqualTo(repo.repositoryFiles.worktreesDirFile.toPath().resolve(worktree))
  }
}
