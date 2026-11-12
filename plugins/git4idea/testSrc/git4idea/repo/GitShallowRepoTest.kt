// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vcs.VcsException
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitShallowCloneOptions
import git4idea.fetch.GitFetchSupport
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import git4idea.test.registerRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.name

@TestApplication
internal class GitShallowRepoTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test shallow repo detection`(): Unit = with(context) {
    val copyRepo = setupShallowClone()

    assertThat(repo.info.isShallow).isFalse()
    assertThat(copyRepo.info.isShallow).isTrue()
  }

  @Test
  fun `test fetch unshallow repository`(): Unit = with(context) {
    val copyRepo = setupShallowClone()
    val fetchSupport = GitFetchSupport.fetchSupport(project)
    val remote = fetchSupport.getDefaultRemoteToFetch(copyRepo)

    assertThat(remote).isNotNull()
    val fetchResult = fetchSupport.fetchUnshallow(copyRepo, remote!!)
    fetchResult.throwExceptionIfFailed()

    GitVcs.getInstance(project).vfsListener.waitForExternalFilesEventsProcessedInTestMode()

    assertThat(copyRepo.info.isShallow).isFalse()
  }

  @Test
  fun `test normal repository can't be unshallowed`(): Unit = with(context) {
    val cloneResult = createClone(null)
    val fetchSupport = GitFetchSupport.fetchSupport(project)
    val remote = fetchSupport.getDefaultRemoteToFetch(cloneResult)
    assertThat(remote).isNotNull()
    val fetchResult = fetchSupport.fetchUnshallow(cloneResult, remote!!)
    assertThrows<VcsException> {
      fetchResult.throwExceptionIfFailed()
    }
  }

  private fun GitSingleRepoContext.setupShallowClone(): GitRepository {
    makeCommit("1.txt")
    makeCommit("2.txt")
    makeCommit("3.txt")

    return createClone(GitShallowCloneOptions(1))
  }

  private fun GitSingleRepoContext.createClone(shallowCloneOptions: GitShallowCloneOptions? = null): GitRepository {
    val copy = projectNioRoot.resolve("copy")
    val cloneResult = Git.getInstance().clone(project,
                                              copy.parent,
                                              "file://${repo.root.path}", copy.name, shallowCloneOptions)
    assertThat(cloneResult.success()).isTrue()

    return registerRepo(project, copy)
  }
}
