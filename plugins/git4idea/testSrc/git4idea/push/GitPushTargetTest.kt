// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitPushTargetTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @BeforeEach
  fun beforeEach() {
    context.addRemote("origin")
  }

  @Test
  fun `test no push spec`(): Unit = with(context) {
    val target = GitPushTarget.getFromPushSpec(repo, repo.currentBranch!!)
    assertThat(target).isNull()
  }

  @Test
  fun `test refs for master`(): Unit = with(context) {
    setPushSpec("origin", "HEAD:refs/for/master")
    assertSpecialTargetRef("refs/for/master", "origin")
  }

  @Test
  fun `test wildcard special ref`(): Unit = with(context) {
    setPushSpec("origin", "refs/heads/*:refs/for/*")
    assertSpecialTargetRef("refs/for/master", "origin")
  }

  @Test
  fun `test complex remote name`(): Unit = with(context) {
    addRemote("my/remote")
    setPushSpec("my/remote", "HEAD:refs/for/master")
    setTracking("master", "my/remote", "refs/heads/master")

    assertSpecialTargetRef("refs/for/master", "my/remote")
  }

  @Test
  fun `test standard fetch refspec`(): Unit = with(context) {
    setPushSpec("origin", "refs/heads/*:refs/remotes/origin/*")
    assertStandardRemoteBranch("master", repo.branches.findBranchByName("origin/master")!!)
  }

  @Test
  fun `test tracked remote is preferable over origin`(): Unit = with(context) {
    addRemote("github")
    setPushSpec("origin", "HEAD:refs/for/origin")
    setPushSpec("github", "HEAD:refs/for/github")
    setTracking("master", "github", "refs/heads/master")

    assertSpecialTargetRef("refs/for/github", "github")
  }

  private fun GitSingleRepoContext.addRemote(remoteName: String) {
    git("remote add $remoteName http://example.git")
    git("update-ref refs/remotes/$remoteName/master HEAD")
    repo.update()
  }

  private fun GitSingleRepoContext.setPushSpec(remote: String, pushSpec: String) {
    git("config remote.$remote.push $pushSpec")
    repo.update()
  }

  private fun GitSingleRepoContext.setTracking(branch: String, remote: String, remoteBranch: String) {
    git("config branch.$branch.remote $remote")
    git("config branch.$branch.merge $remoteBranch")
    repo.update()
  }

  private fun GitSingleRepoContext.assertSpecialTargetRef(expectedRefName: String, expectedRemoteName: String) {
    val target = GitPushTarget.getFromPushSpec(repo, repo.currentBranch!!)
    assertThat(target).isNotNull()
    assertThat(target!!.isSpecialRef).isTrue()
    assertThat(target.presentation).isEqualTo(expectedRefName)
    assertRemoteBranch(expectedRefName, expectedRefName, expectedRemoteName, target.branch)
  }

  private fun assertRemoteBranch(
    nameForLocalOperations: String,
    nameForRemoteOperations: String,
    remoteName: String,
    actualRemoteBranch: GitRemoteBranch,
  ) {
    assertThat(actualRemoteBranch.nameForLocalOperations).isEqualTo(nameForLocalOperations)
    assertThat(actualRemoteBranch.nameForRemoteOperations).isEqualTo(nameForRemoteOperations)
    assertThat(actualRemoteBranch.remote.name).isEqualTo(remoteName)
  }

  private fun GitSingleRepoContext.assertStandardRemoteBranch(expectedPresentation: String, expectedBranch: GitBranch) {
    val target = GitPushTarget.getFromPushSpec(repo, repo.currentBranch!!)
    assertThat(target).isNotNull()
    assertThat(target!!.isSpecialRef).isFalse()
    assertThat(target.presentation).isEqualTo(expectedPresentation)
    assertThat(target.branch).isEqualTo(expectedBranch)
  }
}
