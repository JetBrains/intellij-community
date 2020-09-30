// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.repo.GitRepository
import git4idea.test.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Path

class GitMultiRepoUpdateTest : GitUpdateBaseTest() {
  private lateinit var repository: GitRepository
  private lateinit var community: GitRepository
  private lateinit var bro: Path
  private lateinit var bromunity: Path

  override fun setUp() {
    super.setUp()

    val mainRepo = setupRepositories(projectPath, "parent", "bro")
    repository = mainRepo.projectRepo
    bro = mainRepo.bro

    val communityDir = File(projectPath, "community")
    assertTrue(communityDir.mkdir())
    val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")
    community = enclosingRepo.projectRepo
    bromunity = enclosingRepo.bro

    repository.update()
    community.update()
  }

  fun `test update only roots with incoming changes`() {
    cd(bro)
    tacp("file")
    val hash = last()

    val updatedRepos = mutableListOf<GitRepository>()
    git.mergeListener = {
      updatedRepos.add(it)
    }

    updateWithMerge()

    assertTrue("Main repository should have been updated", updatedRepos.contains(repository))
    assertFalse("Nested repository shouldn't be updated", updatedRepos.contains(community))
    assertEquals("Couldn't find the hash from bro", hash, git("log -1 --no-merges --pretty=%H"))
  }

  fun `test update fails if branch is deleted in one of repositories`() {
    assumeTrue("Not tested: fetch --prune doesn't work in Git ${vcs.version}",
               GitVersionSpecialty.SUPPORTS_FETCH_PRUNE.existsIn(vcs.version))

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

      assertEquals("Update result is incorrect", GitUpdateResult.NOT_READY, result)
      assertErrorNotification("Can't update", GitUpdateProcess.getNoTrackedBranchError(community, "feature"))
    }
    finally {
      settings.syncSetting = syncSetting
    }
  }

  fun `test skip repo in detached HEAD`() {
    cd(bro)
    tac("bro.txt")
    git("push")

    community.checkout("HEAD^0")

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)

    assertEquals("Update result is incorrect", GitUpdateResult.SUCCESS, result)
    assertNoErrorNotification()   // the notification is produced by the common code which we don't call
  }

  fun `test notify error if all repos are in detached HEAD`() {
    cd(bro)
    tac("bro.txt")
    git("push")
    cd(bromunity)
    tac("com.txt")
    git("push")

    repositories().forEach { it.checkout("HEAD^0")}

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)
    assertEquals("Update result is incorrect", GitUpdateResult.NOT_READY, result)
    assertErrorNotification("Can't Update: No Current Branch", GitUpdateProcess.getDetachedHeadErrorNotificationContent(community))
  }

  private fun updateWithMerge(): GitUpdateResult {
    return GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), null, false, true).update(UpdateMethod.MERGE)
  }

  private fun repositories() = listOf(repository, community)
}