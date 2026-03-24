// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.vcs.log.util.VcsUserUtil
import git4idea.GitDisposable
import git4idea.isRemoteBranchProtected
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

class GitRecentCommitsProviderTest : GitSingleRepoTest() {
  private val scope: CoroutineScope get() = GitDisposable.getInstance(project).coroutineScope

  fun `test recent commits are returned`() {
    var loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 1).getRecentCommits(repo.root) }
    assertSize(1, loadedCommits)

    makeCommit("file.txt")
    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 1).getRecentCommits(repo.root) }
    assertSize(1, loadedCommits)

    repeat(3) { makeCommit("file.txt") }
    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 2).getRecentCommits(repo.root) }
    assertSize(2, loadedCommits)

    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 100).getRecentCommits(repo.root) }
    assertSize(5, loadedCommits)
  }

  fun `test can load commits from all users`() {
    makeCommit("file.txt")
    makeCommit(VcsUserUtil.createUser("Richard Roe", "richard.roe@example.com"), "file.txt")

    val provider = GitRecentCommitsProvider(project, scope, 100)
    val myCommits = runBlocking {
      provider.getRecentCommits(repo.root) // current user only by default
    }
    assertSize(2, myCommits)

    val providerAllUsers = GitRecentCommitsProvider(project, scope, 100, userScope = GitRecentCommitsProvider.UserScope.ALL_USERS)
    val allCommits = runBlocking { providerAllUsers.getRecentCommits(repo.root) }
    assertSize(3, allCommits)
  }

  fun `test stop at first merge commit`() {
    makeCommit("base.txt")
    git("checkout -b feature")
    makeCommit("feature.txt")
    git("checkout master")
    git("merge --no-ff feature")

    val afterMerge1 = makeCommit("after1.txt")
    val afterMerge2 = makeCommit("after2.txt")

    val providerStopAtMerge = GitRecentCommitsProvider(project, scope, 100, stopAtFirstMergeCommit = true)
    val commitsBeforeMerge = runBlocking {
      providerStopAtMerge.getRecentCommits(repo.root)
    }
    assertSameElements(commitsBeforeMerge.map { it.id.asString() }, listOf(afterMerge1, afterMerge2))

    val providerAll = GitRecentCommitsProvider(project, scope, 100)
    val allCommits = runBlocking { providerAll.getRecentCommits(repo.root) }
    assertSize(6, allCommits)
  }

  fun `test unpublished filter includes only unpublished commits`() {
    makeCommit("published.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertTrue(isRemoteBranchProtected(listOf(repo), "origin/master"))

    val olderUnpublished = makeCommit("older-unpublished.txt")
    val newerUnpublished = makeCommit("newer-unpublished.txt")
    val head = makeCommit("head.txt")

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertSameElements(listOf(head, newerUnpublished, olderUnpublished),
                       runBlocking { providerUnpublished.getRecentCommits(repo.root) }.map { it.id.asString() })
  }

  fun `test unpublished filter returns empty when head is published`() {
    makeCommit("published.txt")
    makeCommit("head.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertTrue(isRemoteBranchProtected(listOf(repo), "origin/master"))

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertEmpty(runBlocking { providerUnpublished.getRecentCommits(repo.root) })
  }

  fun `test unpublished filter includes commits published only to unprotected branch`() {
    makeCommit("base.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertTrue(isRemoteBranchProtected(listOf(repo), "origin/master"))

    git("checkout -b feature")
    val editableTarget = makeCommit("editable.txt")
    git("push -u origin feature")

    repo.update()
    assertFalse(isRemoteBranchProtected(listOf(repo), "origin/feature"))

    val head = makeCommit("head.txt")

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertSameElements(listOf(head, editableTarget), runBlocking { providerUnpublished.getRecentCommits(repo.root) }.map { it.id.asString() })
  }
}