// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vcs.VcsException
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitShallowCloneOptions
import git4idea.fetch.GitFetchSupport
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import git4idea.test.registerRepo
import kotlin.io.path.name

class GitShallowRepoTest: GitSingleRepoTest() {
  fun `test shallow repo detection`() {
    val copyRepo = setupShallowCLone()

    assertFalse(repo.info.isShallow)
    assertTrue(copyRepo.info.isShallow)
  }

  fun `test fetch unshallow repository`() {
    val copyRepo = setupShallowCLone()
    val fetchSupport = GitFetchSupport.fetchSupport(project)
    val remote = fetchSupport.getDefaultRemoteToFetch(copyRepo)

    assertNotNull(remote)
    val fetchResult = fetchSupport.fetchUnshallow(copyRepo, remote!!)
    fetchResult.throwExceptionIfFailed()

    GitVcs.getInstance(project).vfsListener.waitForExternalFilesEventsProcessedInTestMode()

    assertFalse(copyRepo.info.isShallow)
  }

  fun `test normal repository can't be unshallowed`() {
    val cloneResult = createClone(null)
    val fetchSupport = GitFetchSupport.fetchSupport(project)
    val remote = fetchSupport.getDefaultRemoteToFetch(cloneResult)
    assertNotNull(remote)
    val fetchResult = fetchSupport.fetchUnshallow(cloneResult, remote!!)
    assertThrows(VcsException::class.java) {
      fetchResult.throwExceptionIfFailed()
    }
  }

  private fun setupShallowCLone(): GitRepository {
    makeCommit("1.txt")
    makeCommit("2.txt")
    makeCommit("3.txt")

    return createClone(GitShallowCloneOptions(1))
  }

  private fun createClone(shallowCloneOptions: GitShallowCloneOptions? = null): GitRepository {
    val copy = projectNioRoot.resolve("copy")
    val cloneResult = Git.getInstance().clone(project,
                                              copy.parent.toFile(),
                                              "file://${repo.root.path}", copy.name, shallowCloneOptions)
    assertTrue(cloneResult.success())

    return registerRepo(this.project, copy)
  }
}