// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.HeavyPlatformTestCase.createChildData
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertNotification
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.i18n.GitBundle
import git4idea.push.GitPushNativeResult.Type.ERROR
import git4idea.push.GitPushNativeResult.Type.FORCED_UPDATE
import git4idea.push.GitPushNativeResult.Type.NEW_REF
import git4idea.push.GitPushNativeResult.Type.REJECTED
import git4idea.push.GitPushNativeResult.Type.SUCCESS
import git4idea.push.GitPushNativeResult.Type.UP_TO_DATE
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.MockGitRepository
import git4idea.test.gitPlatformContextFixture
import git4idea.update.GitUpdateResult
import org.junit.jupiter.api.Test

@TestApplication
internal class GitPushResultNotificationTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @Test
  fun `test single success`(): Unit = with(context) {
    val notification = notification(singleResult(SUCCESS, "master", "origin/master", 1))
    assertPushNotification(NotificationType.INFORMATION, "Pushed 1 commit to origin/master", "", notification)
  }

  @Test
  fun `test pushed new branch`(): Unit = with(context) {
    val notification = notification(singleResult(NEW_REF, "feature", "origin/feature", -1))
    assertPushNotification(NotificationType.INFORMATION, "Pushed feature to new branch origin/feature", "", notification)
  }

  @Test
  fun `test force pushed`(): Unit = with(context) {
    val notification = notification(singleResult(FORCED_UPDATE, "feature", "origin/feature", -1))
    assertPushNotification(NotificationType.INFORMATION, "Force pushed feature to origin/feature", "", notification)
  }

  @Test
  fun `test success and fail`(): Unit = with(context) {
    val notification = notification(mapOf(
      repo("ultimate") to repoResult(SUCCESS, "master", "origin/master", 1),
      repo("community") to repoResult(ERROR, "master", "origin/master", "Permission denied")
    ))
    assertPushNotification(NotificationType.ERROR, "Push partially failed",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: Permission denied", notification)
  }

  @Test
  fun `test success and reject`(): Unit = with(context) {
    val notification = notification(mapOf(
      repo("ultimate") to repoResult(SUCCESS, "master", "origin/master", 1),
      repo("community") to repoResult(REJECTED, "master", "origin/master", -1)
    ))
    assertPushNotification(NotificationType.WARNING, "Push partially rejected",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: Push to origin/master was rejected", notification)
  }

  @Test
  fun `test success with update`(): Unit = with(context) {
    val notification = notification(singleResult(SUCCESS, "master", "origin/master", 2, GitUpdateResult.SUCCESS))
    assertPushNotification(NotificationType.INFORMATION, "Pushed 2 commits to origin/master", "", notification)
  }

  @Test
  fun `test success and resolved conflicts`(): Unit = with(context) {
    val notification = notification(mapOf(
      repo("community") to repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS),
      repo("contrib") to repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS),
      repo("ultimate") to repoResult(SUCCESS, "master", "origin/master", 1)
    ))
    assertPushNotification(NotificationType.WARNING, "Push partially rejected",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: $UPDATE_WITH_RESOLVED_CONFLICTS<br/>" +
                           "contrib: $UPDATE_WITH_RESOLVED_CONFLICTS",
                           notification)
  }

  @Test
  fun `test commits and tags`(): Unit = with(context) {
    val branchResult = GitPushNativeResult(SUCCESS, "refs/heads/master")
    val tagResult = GitPushNativeResult(NEW_REF, "refs/tags/v0.1")
    val notification = notification(GitPushRepoResult.convertFromNative(branchResult, listOf(tagResult), 1,
                                                                        from("master"), remoteBranch("origin/master")))
    assertPushNotification(NotificationType.INFORMATION, "Pushed 1 commit to origin/master, and tag v0.1 to origin", "", notification)
  }

  @Test
  fun `test nothing`(): Unit = with(context) {
    val branchResult = GitPushNativeResult(UP_TO_DATE, "refs/heads/master")
    val notification = notification(GitPushRepoResult.convertFromNative(branchResult, emptyList(), 0,
                                                                        from("master"), remoteBranch("origin/master")))
    assertPushNotification(NotificationType.INFORMATION, "Everything is up to date", "", notification)
  }

  @Test
  fun `test only tags`(): Unit = with(context) {
    val branchResult = GitPushNativeResult(UP_TO_DATE, "refs/heads/master")
    val tagResult = GitPushNativeResult(NEW_REF, "refs/tags/v0.1")
    val notification = notification(GitPushRepoResult.convertFromNative(branchResult, listOf(tagResult), 0,
                                                                        from("master"), remoteBranch("origin/master")))
    assertPushNotification(NotificationType.INFORMATION, "Pushed tag v0.1 to origin", "", notification)
  }

  @Test
  fun `test two repo with tags`(): Unit = with(context) {
    val branchSuccess = GitPushNativeResult(SUCCESS, "refs/heads/master")
    val branchUpToDate = GitPushNativeResult(UP_TO_DATE, "refs/heads/master")
    val tagResult = GitPushNativeResult(NEW_REF, "refs/tags/v0.1")
    val comRes = GitPushRepoResult.convertFromNative(branchSuccess, listOf(tagResult), 1, from("master"), remoteBranch("origin/master"))
    val ultRes = GitPushRepoResult.convertFromNative(branchUpToDate, listOf(tagResult), 0, from("master"), remoteBranch("origin/master"))

    val notification = notification(mapOf(
      repo("community") to comRes,
      repo("ultimate") to ultRes
    ))

    assertPushNotification(NotificationType.INFORMATION, "Push successful",
                           "community: Pushed 1 commit to origin/master, and tag v0.1 to origin<br/>" +
                           "ultimate: Pushed tag v0.1 to origin", notification)
  }

  @Test
  fun `test two tags`(): Unit = with(context) {
    val branchResult = GitPushNativeResult(UP_TO_DATE, "refs/heads/master")
    val tag1 = GitPushNativeResult(NEW_REF, "refs/tags/v0.1")
    val tag2 = GitPushNativeResult(NEW_REF, "refs/tags/v0.2")
    val notification = notification(GitPushRepoResult.convertFromNative(branchResult, listOf(tag1, tag2), 0,
                                                                        from("master"), remoteBranch("origin/master")))
    assertPushNotification(NotificationType.INFORMATION, "Pushed 2 tags to origin", "", notification)
  }

  @Test
  fun `test tag no commits`(): Unit = with(context) {
    val notification = pushSingleTagNotification(NEW_REF)
    assertPushNotification(NotificationType.INFORMATION, "Pushed tag v0.1 to origin", "", notification)
  }

  @Test
  fun `test tag no commits up to date`(): Unit = with(context) {
    val notification = pushSingleTagNotification(UP_TO_DATE)
    assertPushNotification(NotificationType.INFORMATION, "Everything is up to date", "", notification)
  }

  @Test
  fun `test tag no commits already exists`(): Unit = with(context) {
    val notification = pushSingleTagNotification(REJECTED)
    assertPushNotification(NotificationType.WARNING, "Push rejected", "Push of v0.1 was rejected by the remote", notification)
  }

  private fun GitPlatformTestContext.pushSingleTagNotification(type: GitPushNativeResult.Type): GitPushResultNotification {
    val tagRef = "refs/tags/v0.1"
    val nativeResult = GitPushNativeResult(type, tagRef)
    val result = GitPushRepoResult.tagPushResult(nativeResult,
                                                 GitPushSource.Tag(GitTag(tagRef)),
                                                 GitSpecialRefRemoteBranch(tagRef, remote("origin")))
    return notification(result)
  }

  private fun GitPlatformTestContext.singleResult(
    type: GitPushNativeResult.Type,
    from: String,
    to: String,
    commits: Int,
    updateResult: GitUpdateResult? = null,
  ): Map<GitRepository, GitPushRepoResult> = mapOf(repo("community") to repoResult(type, from, to, commits, updateResult))

  private fun repoResult(
    nativeType: GitPushNativeResult.Type,
    from: String,
    to: String,
    commits: Int,
    updateResult: GitUpdateResult? = null,
  ): GitPushRepoResult {
    val reason = if (nativeType == REJECTED) GitPushNativeResult.FETCH_FIRST_REASON else null
    val nativeResult = GitPushNativeResult(nativeType, from, reason, null)
    return GitPushRepoResult.addUpdateResult(
      GitPushRepoResult.convertFromNative(nativeResult, emptyList(), commits, from(from), remoteBranch(to)),
      updateResult)
  }

  @Suppress("UNUSED_PARAMETER") // keep params for unification
  private fun repoResult(nativeType: GitPushNativeResult.Type, from: String, to: String, errorText: String): GitPushRepoResult =
    GitPushRepoResult.error(from(from), remoteBranch(to), errorText)

  private fun from(from: String): GitPushSource = GitPushSource.create(GitLocalBranch(from))

  private fun remoteBranch(to: String): GitRemoteBranch {
    val firstSlash = to.indexOf('/')
    return GitStandardRemoteBranch(remote(to.substring(0, firstSlash)), to.substring(firstSlash + 1))
  }

  private fun remote(name: String): GitRemote = GitRemote(name, emptyList(), emptyList(), emptyList(), emptyList())

  private fun GitPlatformTestContext.notification(singleResult: GitPushRepoResult): GitPushResultNotification =
    notification(mapOf(repo("community") to singleResult))

  private fun GitPlatformTestContext.notification(map: Map<GitRepository, GitPushRepoResult>): GitPushResultNotification {
    val wasUpdatePerformed = map.values.any { it.updateResult != null }
    val updatedFiles = UpdatedFiles.create()
    if (wasUpdatePerformed) {
      updatedFiles.topLevelGroups[0].add("file.txt", "Git", null)
    }
    return invokeAndWaitIfNeeded {
      GitPushResultNotification.create(project, GitPushResult(map, updatedFiles, null, null, emptyMap()),
                                       null, map.size > 1, null, emptyMap())
    }
  }

  private fun assertPushNotification(
    type: NotificationType,
    title: String,
    content: String,
    actual: GitPushResultNotification,
  ) {
    assertNotification(type, "", GitPushResultNotification.emulateTitle(title, content), actual)
  }

  private fun GitPlatformTestContext.repo(name: String): MockGitRepository =
    MockGitRepository(project, createChildData(projectRoot, name))

  companion object {
    private val UPDATE_WITH_RESOLVED_CONFLICTS = GitBundle.message("push.notification.description.rejected.and.conflicts")
  }
}
