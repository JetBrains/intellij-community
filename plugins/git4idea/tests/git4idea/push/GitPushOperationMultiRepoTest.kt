// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.commands.GitCommandResult
import git4idea.push.GitRejectedPushUpdateDialog.Companion.PushRejectedExitCode
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.last
import git4idea.test.makeCommit
import git4idea.test.makePushSpec
import git4idea.test.runUnderProgress
import git4idea.test.setupRepositories
import git4idea.test.tac
import git4idea.update.GitUpdateResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.util.Collections

@TestApplication
@EnableTracingFor(categoryClasses = [GitPushOperation::class])
internal class GitPushOperationMultiRepoTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var pushSupport: GitPushSupport
  private lateinit var ultimate: GitRepository
  private lateinit var community: GitRepository

  /** A clone of the same parent repository as [ultimate], outside of the project. */
  private lateinit var brultimate: Path

  /** A clone of the same parent repository as [community], outside of the project. */
  private lateinit var brommunity: Path

  @BeforeEach
  fun beforeEach() {
    with(context) {
      val mainRepo = setupRepositories(projectPath, "parent", "bro")

      val communityDir = File(projectPath, "community")
      check(communityDir.mkdir()) { "Couldn't create $communityDir" }
      val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")

      cd(projectPath)
      refresh()
      updateRepositories()

      pushSupport = gitPushSupport()
      ultimate = mainRepo.projectRepo
      community = enclosingRepo.projectRepo
      brultimate = mainRepo.bro
      brommunity = enclosingRepo.bro
    }

    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @AfterEach
  fun afterEach() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun `test try push from all roots even if one fails`(): Unit = with(context) {
    // fail in the first repo
    git.onPush {
      if (it == ultimate) GitCommandResult.error("Failed to push to origin")
      else null
    }

    cd(ultimate)
    makeCommit("file.txt")
    cd(community)
    makeCommit("com.txt")

    val map = hashMapOf(
      ultimate to makePushSpec(ultimate, "master", "origin/master"),
      community to makePushSpec(community, "master", "origin/master")
    )

    refresh()
    updateChangeListManager()

    val result = runUnderProgress { GitPushOperation(project, pushSupport, map, null, false, false).execute() }

    val result1 = result.results[ultimate]!!
    val result2 = result.results[community]!!

    assertRepoResult(GitPushRepoResult.Type.ERROR, -1, "master", "origin/master", null, result1)
    assertThat(result1.error).describedAs("Error text is incorrect").isEqualTo("Failed to push to origin")
    assertRepoResult(GitPushRepoResult.Type.SUCCESS, 1, "master", "origin/master", null, result2)
  }

  @Test
  fun `test update all roots on reject when needed even if only one in push spec`(): Unit = with(context) {
    cd(brultimate)
    val broHash = makeCommit("bro.txt")
    git("push")
    cd(brommunity)
    val broCommunityHash = makeCommit("bro_com.txt")
    git("push")

    cd(ultimate)
    makeCommit("file.txt")

    val mainSpec = makePushSpec(ultimate, "master", "origin/master")
    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode } // auto-update-all-roots is selected by default

    refresh()
    updateChangeListManager()

    val result = runUnderProgress {
      GitPushOperation(project, pushSupport,
                       Collections.singletonMap(ultimate, mainSpec), null, false, false).execute()
    }

    val result1 = result.results[ultimate]!!
    val result2 = result.results[community]

    assertRepoResult(GitPushRepoResult.Type.SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, result1)
    assertThat(result2).describedAs("This was not pushed => no result should be generated").isNull()

    cd(community)
    assertThat(last()).describedAs("Update in community didn't happen").isEqualTo(broCommunityHash)

    cd(ultimate)
    val lastCommitParents = git("log -1 --pretty=%P").split(" ".toRegex()).dropLastWhile { it.isEmpty() }
    assertThat(lastCommitParents).describedAs("Merge didn't happen in main repository").hasSize(2)
    assertRemoteCommitMerged("Commit from bro repository didn't arrive", broHash)
  }

  // IDEA-169877
  @Test
  fun `test push rejected in one repo when branch is deleted in another, should finally succeed`(): Unit = with(context) {
    listOf(brultimate, brommunity).forEach {
      cd(it)
      git("checkout -b feature")
      git("push -u origin feature")
    }
    listOf(ultimate, community).forEach {
      cd(it)
      git("pull")
      git("checkout -b feature origin/feature")
    }

    // commit in one repo to reject the push
    cd(brultimate)
    val broHash = tac("bro.txt")
    git("push")
    // remove branch in another repo
    cd(brommunity)
    git("push origin :feature")

    cd(ultimate)
    val commitToPush = tac("file.txt")

    listOf(ultimate, community).forEach { it.update() }

    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode } // auto-update-all-roots is selected by default

    refresh()
    updateChangeListManager()

    // push only to 1 repo, otherwise the push would recreate the deleted branch, and the error won't reproduce
    val pushSpecs = mapOf(ultimate to makePushSpec(ultimate, "feature", "origin/feature"))
    val result = runUnderProgress { GitPushOperation(project, pushSupport, pushSpecs, null, false, false).execute() }

    val result1 = result.results[ultimate]!!
    assertRepoResult(GitPushRepoResult.Type.SUCCESS, 2, "feature", "origin/feature", GitUpdateResult.SUCCESS, result1)
    assertRemoteCommitMerged("Commit from bro repository didn't arrive", broHash)

    cd(brultimate)
    git("pull origin feature")
    assertThat(git("log --no-walk HEAD^1 --pretty=%H"))
      .describedAs("Commit from ultimate repository wasn't pushed")
      .isEqualTo(commitToPush)
  }

  private fun GitPlatformTestContext.assertRemoteCommitMerged(message: String, expectedHash: String) {
    assertThat(git("log --no-walk HEAD^2 --pretty=%H")).describedAs(message).isEqualTo(expectedHash)
  }
}
