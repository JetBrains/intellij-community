// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import kotlinx.coroutines.runBlocking

class GitMyRecentCommitsProviderTest : GitSingleRepoTest() {
  fun `test recent commits are returned`() {
    val provider = GitMyRecentCommitsProvider.getInstance(project)
    var loadedCommits = runBlocking { provider.getRecentCommits(repo.root, 1) }
    assertSize(1, loadedCommits)

    makeCommit("file.txt")
    loadedCommits = runBlocking { provider.getRecentCommits(repo.root, 1) }
    assertSize(1, loadedCommits)

    repeat(3) { makeCommit("file.txt") }
    loadedCommits = runBlocking { provider.getRecentCommits(repo.root, 2) }
    assertSize(2, loadedCommits)

    loadedCommits = runBlocking { provider.getRecentCommits(repo.root, 100) }
    assertSize(5, loadedCommits)
  }
}