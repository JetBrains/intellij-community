// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.vcs.log.impl.HashImpl
import git4idea.config.UpdateMethod
import git4idea.config.UpdateMethod.REBASE
import git4idea.config.UpdateMethod.RESET
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.addCommit
import git4idea.test.assertStatus
import git4idea.test.cd
import git4idea.test.checkout
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.last
import git4idea.test.resolveConflicts
import git4idea.test.tac
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class GitSingleRepoUpdateTest : GitUpdateBaseTest() {
  private lateinit var repo: GitRepository
  private lateinit var broRepo: Path

  override fun setUp() {
    super.setUp()

    repo = createRepository(project, projectNioRoot, true)
    cd(projectPath)

    val parent = prepareRemoteRepo(repo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    repo.update()
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#git4idea.update")

  fun `test stash is called for rebase if there are local changes and local commits`() {
    commitAndPush(broRepo)
    tac("a.txt")
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertTrue("Stash should have been called for dirty working tree", stashCalled)
    repo.assertStatus(localFile, 'M')
  }

  // "Fast-forward merge" optimization
  fun `test stash is not called for rebase if there are local changes, but no local commits`() {
    commitAndPush(broRepo)
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertFalse("Stash shouldn't be called, because of fast-forward merge optimization", stashCalled)
    repo.assertStatus(localFile, 'A')
  }

  // IDEA-167688
  fun `test stash is not called for rebase if there are no local changes`() {
    commitAndPush(broRepo)

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    updateWith(REBASE)
    assertFalse("Stash shouldn't be called for clean working tree", stashCalled)
  }

  // IDEA-76760
  fun `test stash is called for rebase in case of AD changes`() {
    commitAndPush(broRepo)

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    cd(repo)
    val file = file("a.txt").create().add().delete().file
    updateChangeListManager()

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertTrue("Stash should be called for clean working tree", stashCalled)
    repo.assertStatus(file, 'A')
  }

  fun `test update range if only incoming commits`() {
    cd(broRepo)
    val before = last().asHash()
    commitSomethingToBroRepo()
    commitSomethingToBroRepo()
    val after = last().asHash()
    git("push -u origin master")

    val (_, updateProcess) = updateWith(REBASE)

    val range = getUpdatedRange(updateProcess)
    assertEquals("Updated range is incorrect", HashRange(before, after), range)
  }

  fun `test update range if tracked branch has been fetched before update`() {
    cd(broRepo)
    val before = last().asHash()
    commitSomethingToBroRepo()
    git("push -u origin master")

    cd(repo)
    git("fetch")

    cd(broRepo)
    commitSomethingToBroRepo()
    val after = last().asHash()
    git("push -u origin master")

    val (_, updateProcess) = updateWith(REBASE)

    val range = getUpdatedRange(updateProcess)
    assertEquals("Updated range is incorrect", HashRange(before, after), range)
  }

  fun `test update range if there are unpushed commits`() {
    cd(broRepo)
    val before = last().asHash()
    commitSomethingToBroRepo()
    commitSomethingToBroRepo()
    val after = last().asHash()
    git("push -u origin master")

    cd(repo)
    file("local.txt").append("initial content\n").addCommit("created local.txt")

    val (_, updateProcess) = updateWith(REBASE)

    val range = getUpdatedRange(updateProcess)
    assertEquals("Updated range is incorrect", HashRange(before, after), range)
  }

  fun `test local branch equals remote after reset update`() {
    repeat(3) {
      commitAndPush(broRepo)
    }

    cd(broRepo)
    val remoteHead = last()

    cd(repo)

    val localFiles = listOf(file("local1.txt"), file("local2.txt"))
    localFiles.forEach { it.create().addCommit("local commit") }

    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)

    assertEquals("Local branch should equal remote after reset", remoteHead, last())
    assertTrue("Files from local commits should not exist after reset", localFiles.none { it.exists() })
  }

  fun `test non conflicting local changes persist after reset update`() {
    commitAndPush(broRepo)

    val localFiles = listOf(file("local1.txt"), file("local2.txt"))
    localFiles.forEach { it.create() }

    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)

    assertTrue("Locally changed uncommited files should exist after reset", localFiles.all { it.exists() })
  }

  fun `test stash is called for reset update and merge dialog is shown if there are conflicting tracked local changes`() {
    commitAndPush(broRepo)
    val localFile = file("bro.txt").create("local content").add().file // bro.txt exists in broRepo
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }
    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)
    assertTrue("Stash should have been called for dirty working tree", stashCalled)
    assertTrue(vcsHelper.mergeDialogWasShown())
    repo.assertStatus(localFile, 'M')
  }

  fun `test update range on reset update for diverged branches`() {
    commitAndPush(broRepo)

    updateWith(REBASE) // fast-forward

    val before = last().asHash()
    repeat(3) {
      commitAndPush(broRepo)
    }
    cd(broRepo)
    val after = last().asHash()

    cd(repo)

    val localFiles = listOf(file("local1.txt"), file("local2.txt"))
    localFiles.forEach { it.create().addCommit("local commit") }

    val (_, updateProcess) = updateWith(RESET)
    val range = getUpdatedRange(updateProcess)

    assertEquals("Updated range is incorrect", HashRange(before, after), range)
  }

  fun `test reset update when remote branch is not set`() {
    repeat(3) {
      commitAndPush(broRepo)
    }

    cd(repo)
    git("branch --unset-upstream master")

    updateWith(RESET)

    assertErrorNotification(GitBundle.message("update.notification.update.error"),
                            GitUpdateProcess.getNoTrackedBranchError(repo, "master"))
  }

  fun `test reset update when remote branch is deleted`() {
    cd(broRepo)
    checkout("-b feature")
    commitSomethingToBroRepo()
    git("push -u origin feature")

    cd(repo)
    git("fetch")
    checkout("-b feature origin/feature")

    cd(broRepo)
    git("push --delete origin feature")

    cd(repo)
    checkout("feature")
    updateWith(RESET)

    assertErrorNotification(GitBundle.message("update.notification.update.error"),
                            GitUpdateProcess.getNoTrackedBranchError(repo, "feature"))
  }

  private fun getUpdatedRange(updateProcess: GitUpdateProcess): HashRange {
    return requireNotNull(updateProcess.updatedRanges)[repo].let(::requireNotNull)
  }

  private fun updateWith(method: UpdateMethod): Pair<GitUpdateResult, GitUpdateProcess> =
    runBlocking {
      coroutineToIndicator { indicator ->
        val process = GitUpdateProcess(project, indicator, listOf(repo), UpdatedFiles.create(), null, false, true)
        return@coroutineToIndicator process.update(method) to process
      }
    }

  private fun commitAndPush(path: Path) {
    cd(path)
    commitSomethingToBroRepo()
    git("push -u origin master")
    cd(repo)
  }

  private fun commitSomethingToBroRepo() {
    cd(broRepo)
    val file = broRepo.resolve("bro.txt")
    val content = "content-${Math.random()}\n"
    FileUtil.writeToFile(file.toFile(), content.toByteArray(), true)
    addCommit("modified bro.txt")
  }

  private fun String.asHash() = HashImpl.build(this)

  private fun assertSuccessfulUpdate(result: GitUpdateResult) {
    assertEquals("Incorrect update result", GitUpdateResult.SUCCESS, result)
  }

  internal fun file(path: String) = repo.file(path)
}