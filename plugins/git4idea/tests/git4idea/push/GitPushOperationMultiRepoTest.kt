// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push

import com.intellij.dvcs.push.PushSpec
import com.intellij.openapi.vcs.Executor.cd
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.update.GitUpdateResult
import java.io.File
import java.nio.file.Path
import java.util.*

class GitPushOperationMultiRepoTest : GitPushOperationBaseTest() {

  private lateinit var community: GitRepository
  private lateinit var ultimate: GitRepository

  private lateinit var brultimate: Path
  private lateinit var brommunity: Path

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val mainRepo = setupRepositories(projectPath, "parent", "bro")
    ultimate = mainRepo.projectRepo
    brultimate = mainRepo.bro

    val communityDir = File(projectPath, "community")
    assertTrue(communityDir.mkdir())
    val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")
    community = enclosingRepo.projectRepo
    brommunity = enclosingRepo.bro

    cd(projectPath)
    refresh()
    updateRepositories()
  }

  fun `test try push from all roots even if one fails`() {
    // fail in the first repo
    git.onPush {
      if (it == ultimate) GitCommandResult(false, 128, listOf("Failed to push to origin"), listOf<String>())
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

    val result = GitPushOperation(project, pushSupport, map, null, false, false).execute()

    val result1 = result.results[ultimate]!!
    val result2 = result.results[community]!!

    assertResult(GitPushRepoResult.Type.ERROR, -1, "master", "origin/master", null, result1)
    assertEquals("Error text is incorrect", "Failed to push to origin", result1.error)
    assertResult(GitPushRepoResult.Type.SUCCESS, 1, "master", "origin/master", null, result2)
  }

  fun `test update all roots on reject when needed even if only one in push spec`() {
    cd(brultimate)
    val broHash = makeCommit("bro.txt")
    git("push")
    cd(brommunity)
    val broCommunityHash = makeCommit("bro_com.txt")
    git("push")

    cd(ultimate)
    makeCommit("file.txt")

    val mainSpec = makePushSpec(ultimate, "master", "origin/master")
    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE) // auto-update-all-roots is selected by default

    refresh()
    updateChangeListManager()

    val result = GitPushOperation(project, pushSupport,
                                  Collections.singletonMap<GitRepository, PushSpec<GitPushSource, GitPushTarget>>(ultimate, mainSpec), null, false, false).execute()

    val result1 = result.results[ultimate]!!
    val result2 = result.results[community]

    assertResult(GitPushRepoResult.Type.SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, result1)
    assertNull(result2) // this was not pushed => no result should be generated

    cd(community)
    val lastHash = last()
    assertEquals("Update in community didn't happen", broCommunityHash, lastHash)

    cd(ultimate)
    val lastCommitParents = git("log -1 --pretty=%P").split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    assertEquals("Merge didn't happen in main repository", 2, lastCommitParents.size)
    assertRemoteCommitMerged("Commit from bro repository didn't arrive", broHash)
  }

  // IDEA-169877
  fun `test push rejected in one repo when branch is deleted in another, should finally succeed`() {
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

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE) // auto-update-all-roots is selected by default

    refresh()
    updateChangeListManager()

    // push only to 1 repo, otherwise the push would recreate the deleted branch, and the error won't reproduce
    val pushSpecs = mapOf(ultimate to makePushSpec(ultimate, "feature", "origin/feature"))
    val result = GitPushOperation(project, pushSupport, pushSpecs, null, false, false).execute()

    val result1 = result.results[ultimate]!!
    assertResult(GitPushRepoResult.Type.SUCCESS, 2, "feature", "origin/feature", GitUpdateResult.SUCCESS, result1)
    assertRemoteCommitMerged("Commit from bro repository didn't arrive", broHash)

    cd(brultimate)
    git("pull origin feature")
    assertEquals("Commit from ultimate repository wasn't pushed", commitToPush, git("log --no-walk HEAD^1 --pretty=%H"))
  }

  private fun assertRemoteCommitMerged(message: String, expectedHash: String) {
    assertEquals(message, expectedHash, git("log --no-walk HEAD^2 --pretty=%H"))
  }
}
