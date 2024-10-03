// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import git4idea.commands.Git
import git4idea.commands.GitShallowCloneOptions
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import git4idea.test.registerRepo
import kotlin.io.path.name

class GitShallowRepoTest: GitSingleRepoTest() {
  fun `test shallow repo detection`() {
    makeCommit("1.txt")
    makeCommit("2.txt")
    makeCommit("3.txt")

    assertFalse(repo.info.isShallow)

    val copy = projectNioRoot.resolve("copy")
    val cloneResult = Git.getInstance().clone(project,
                                              copy.parent.toFile(),
                                              "file://${repo.root.path}", copy.name, GitShallowCloneOptions(1))
    assertTrue(cloneResult.success())
    val copyRepo = registerRepo(project, copy)
    assertTrue(copyRepo.info.isShallow)
  }
}