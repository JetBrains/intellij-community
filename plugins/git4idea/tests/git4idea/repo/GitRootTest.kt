// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import git4idea.GitUtil
import git4idea.test.GitSingleRepoTest

class GitRootTest : GitSingleRepoTest() {
  fun `test non-existing dir`() {
    val childDir = projectNioRoot.resolve("child")
    assertNull(GitUtil.findGitDir(childDir))
  }

  fun `test not git repo`() {
    assertNull(GitUtil.findGitDir(testNioRoot))
  }

  fun `test simple repo`() {
    assertEquals(projectNioRoot.resolve(".git"), GitUtil.findGitDir(repo.root.toNioPath()))
  }

  fun `test git worktree`() {
    val worktree = "test"
    git("worktree add $worktree")

    assertEquals(repo.repositoryFiles.worktreesDirFile.toPath().resolve(worktree), GitUtil.findGitDir(projectNioRoot.resolve(worktree)))
  }
}