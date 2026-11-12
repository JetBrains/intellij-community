// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertNoErrorNotification
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.checkout
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.last
import git4idea.test.tac
import git4idea.test.tacp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class GitMultiRepoUpdateTest {

  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH).gitMultiRepoUpdateFixture()
  private val context get() = contextFixture.get()

  @Test
  fun `test update only roots with incoming changes`(): Unit = with(context) {
    cd(bro)
    tacp("file")
    val hash = last()

    val updatedRepos = mutableListOf<GitRepository>()
    git.mergeListener = {
      updatedRepos.add(it)
    }

    updateWithMerge()

    assertThat(updatedRepos).describedAs("Main repository should have been updated").contains(repository)
    assertThat(updatedRepos).describedAs("Nested repository shouldn't be updated").doesNotContain(community)
    assertThat(git("log -1 --no-merges --pretty=%H")).describedAs("Couldn't find the hash from bro").isEqualTo(hash)
  }

  @Test
  fun `test update fails if branch is deleted in one of repositories`(): Unit = with(context) {
    assumeTrue(GitVersionSpecialty.SUPPORTS_FETCH_PRUNE.existsIn(vcs.version),
               "Not tested: fetch --prune doesn't work in Git ${vcs.version}")

    val syncSetting = settings.syncSetting
    try {
      settings.syncSetting = DvcsSyncSettings.Value.SYNC

      listOf(bro, bromunity).forEach {
        cd(it)
        git("checkout -b feature")
        git("push -u origin feature")
      }
      listOf(repository, community).forEach {
        cd(it)
        git("pull")
        git("checkout -b feature origin/feature")
        it.update()
      }

      // commit in one repo to let update work
      cd(bro)
      tac("bro.txt")
      git("push")
      // remove branch in another repo
      cd(bromunity)
      git("push origin :feature")

      val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
      val result = updateProcess.update(UpdateMethod.MERGE)

      assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.NOT_READY)
      assertErrorNotification("Cannot update", GitUpdateProcess.getNoTrackedBranchError(community, "feature"))
    }
    finally {
      settings.syncSetting = syncSetting
    }
  }

  @Test
  fun `test skip repo in detached HEAD`(): Unit = with(context) {
    cd(bro)
    tac("bro.txt")
    git("push")

    community.checkout("HEAD^0")

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)

    assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.SUCCESS)
    assertNoErrorNotification()   // the notification is produced by the common code which we don't call
  }

  @Test
  fun `test notify error if all repos are in detached HEAD`(): Unit = with(context) {
    cd(bro)
    tac("bro.txt")
    git("push")
    cd(bromunity)
    tac("com.txt")
    git("push")

    repositories().forEach { it.checkout("HEAD^0") }

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)

    assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.NOT_READY)
    assertErrorNotification(GitBundle.message("notification.title.can.t.update.no.current.branch"),
                            GitUpdateProcess.getDetachedHeadErrorNotificationContent(community))
  }

  private fun GitPlatformTestContext.updateWithMerge(): GitUpdateResult {
    return GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
      .update(UpdateMethod.MERGE)
  }

  private fun repositories() = with(context) { listOf(repository, community) }
}
