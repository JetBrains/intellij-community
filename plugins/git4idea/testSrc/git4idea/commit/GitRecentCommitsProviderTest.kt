// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.util.VcsUserUtil
import git4idea.GitDisposable
import git4idea.isRemoteBranchProtected
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import git4idea.test.prepareRemoteRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitRecentCommitsProviderTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val GitSingleRepoContext.scope: CoroutineScope get() = GitDisposable.getInstance(project).coroutineScope

  @Test
  fun `test recent commits are returned`(): Unit = with(context) {
    var loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 1).getRecentCommits(repo.root) }
    assertThat(loadedCommits).hasSize(1)

    makeCommit("file.txt")
    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 1).getRecentCommits(repo.root) }
    assertThat(loadedCommits).hasSize(1)

    repeat(3) { makeCommit("file.txt") }
    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 2).getRecentCommits(repo.root) }
    assertThat(loadedCommits).hasSize(2)

    loadedCommits = runBlocking { GitRecentCommitsProvider(project, scope, 100).getRecentCommits(repo.root) }
    assertThat(loadedCommits).hasSize(5)
  }

  @Test
  fun `test can load commits from all users`(): Unit = with(context) {
    makeCommit("file.txt")
    makeCommit(VcsUserUtil.createUser("Richard Roe", "richard.roe@example.com"), "file.txt")

    val provider = GitRecentCommitsProvider(project, scope, 100)
    val myCommits = runBlocking {
      provider.getRecentCommits(repo.root) // current user only by default
    }
    assertThat(myCommits).hasSize(2)

    val providerAllUsers = GitRecentCommitsProvider(project, scope, 100, userScope = GitRecentCommitsProvider.UserScope.ALL_USERS)
    val allCommits = runBlocking { providerAllUsers.getRecentCommits(repo.root) }
    assertThat(allCommits).hasSize(3)
  }

  @Test
  fun `test stop at first merge commit`(): Unit = with(context) {
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
    assertThat(commitsBeforeMerge.map { it.id.asString() }).containsExactlyInAnyOrder(afterMerge1, afterMerge2)

    val providerAll = GitRecentCommitsProvider(project, scope, 100)
    val allCommits = runBlocking { providerAll.getRecentCommits(repo.root) }
    assertThat(allCommits).hasSize(6)
  }

  @Test
  fun `test unpublished filter includes only unpublished commits`(): Unit = with(context) {
    makeCommit("published.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertThat(isRemoteBranchProtected(listOf(repo), "origin/master")).isTrue()

    val olderUnpublished = makeCommit("older-unpublished.txt")
    val newerUnpublished = makeCommit("newer-unpublished.txt")
    val head = makeCommit("head.txt")

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertThat(runBlocking { providerUnpublished.getRecentCommits(repo.root) }.map { it.id.asString() })
      .containsExactlyInAnyOrder(head, newerUnpublished, olderUnpublished)
  }

  @Test
  fun `test unpublished filter returns empty when head is published`(): Unit = with(context) {
    makeCommit("published.txt")
    makeCommit("head.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertThat(isRemoteBranchProtected(listOf(repo), "origin/master")).isTrue()

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertThat(runBlocking { providerUnpublished.getRecentCommits(repo.root) }).isEmpty()
  }

  @Test
  fun `test unpublished filter includes commits published only to unprotected branch`(): Unit = with(context) {
    makeCommit("base.txt")
    prepareRemoteRepo(repo)
    git("push -u origin master")

    repo.update()
    assertThat(isRemoteBranchProtected(listOf(repo), "origin/master")).isTrue()

    git("checkout -b feature")
    val editableTarget = makeCommit("editable.txt")
    git("push -u origin feature")

    repo.update()
    assertThat(isRemoteBranchProtected(listOf(repo), "origin/feature")).isFalse()

    val head = makeCommit("head.txt")

    val providerUnpublished = GitRecentCommitsProvider(project, scope, 100, unpublishedOnly = true)
    assertThat(runBlocking { providerUnpublished.getRecentCommits(repo.root) }.map { it.id.asString() })
      .containsExactlyInAnyOrder(head, editableTarget)
  }
}