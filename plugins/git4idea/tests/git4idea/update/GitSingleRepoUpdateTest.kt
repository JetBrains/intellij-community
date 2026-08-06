// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.updateChangeListManager
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.UpdateMethod
import git4idea.config.UpdateMethod.REBASE
import git4idea.config.UpdateMethod.RESET
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.TestFile
import git4idea.test.assertStatus
import git4idea.test.cd
import git4idea.test.checkout
import git4idea.test.createBroRepo
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.last
import git4idea.test.modify
import git4idea.test.prepareRemoteRepo
import git4idea.test.resolveConflicts
import git4idea.test.runUnderProgress
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertNotEquals

@TestApplication
internal class GitSingleRepoUpdateTest {
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repo: GitRepository
  private lateinit var broRepo: Path

  @BeforeEach
  fun setUp() {
    with(context) {
      repo = createRepository(project, projectNioRoot, true)
      cd(projectPath)

      val parent = prepareRemoteRepo(repo)
      git("push -u origin master")
      broRepo = createBroRepo("bro", parent)
      repo.update()
    }
  }

  @Test
  fun `test stash is called for rebase if there are local changes and local commits`(): Unit = with(context) {
    commitAndPushFromBro()
    tac("a.txt")
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertThat(stashCalled).describedAs("Stash should have been called for dirty working tree").isTrue()
    repo.assertStatus(localFile, 'M')
  }

  // "Fast-forward merge" optimization
  @Test
  fun `test stash is not called for rebase if there are local changes, but no local commits`(): Unit = with(context) {
    commitAndPushFromBro()
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertThat(stashCalled).describedAs("Stash shouldn't be called, because of fast-forward merge optimization").isFalse()
    repo.assertStatus(localFile, 'A')
  }

  // IDEA-167688
  @Test
  fun `test stash is not called for rebase if there are no local changes`(): Unit = with(context) {
    commitAndPushFromBro()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    updateWith(REBASE)
    assertThat(stashCalled).describedAs("Stash shouldn't be called for clean working tree").isFalse()
  }

  // IDEA-76760
  @Test
  fun `test stash is called for rebase in case of AD changes`(): Unit = with(context) {
    commitAndPushFromBro()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    cd(repo)
    val addedDeletedFile = file("a.txt").create().add().delete().file
    updateChangeListManager()

    val (result, _) = updateWith(REBASE)
    assertSuccessfulUpdate(result)
    assertThat(stashCalled).describedAs("Stash should be called for clean working tree").isTrue()
    repo.assertStatus(addedDeletedFile, 'A')
  }

  @Test
  fun `test update range if only incoming commits`(): Unit = with(context) {
    cd(broRepo)
    val before = last().asHash()
    commitSomethingToBroRepo()
    commitSomethingToBroRepo()
    val after = last().asHash()
    git("push -u origin master")

    val (_, updateProcess) = updateWith(REBASE)

    assertThat(getUpdatedRange(updateProcess)).describedAs("Updated range is incorrect").isEqualTo(HashRange(before, after))
  }

  @Test
  fun `test update range if tracked branch has been fetched before update`(): Unit = with(context) {
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

    assertThat(getUpdatedRange(updateProcess)).describedAs("Updated range is incorrect").isEqualTo(HashRange(before, after))
  }

  @Test
  fun `test update range if there are unpushed commits`(): Unit = with(context) {
    cd(broRepo)
    val before = last().asHash()
    commitSomethingToBroRepo()
    commitSomethingToBroRepo()
    val after = last().asHash()
    git("push -u origin master")

    cd(repo)
    file("local.txt").append("initial content\n").addCommit("created local.txt")

    val (_, updateProcess) = updateWith(REBASE)

    assertThat(getUpdatedRange(updateProcess)).describedAs("Updated range is incorrect").isEqualTo(HashRange(before, after))
  }

  @Test
  fun `test local branch equals remote after reset update`(): Unit = with(context) {
    repeat(3) {
      commitAndPushFromBro()
    }

    cd(broRepo)
    val remoteHead = last()

    cd(repo)

    val localFiles = commitLocalFilesToRepo()

    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)

    assertThat(last()).describedAs("Local branch should equal remote after reset").isEqualTo(remoteHead)
    assertThat(localFiles.none { it.exists() }).describedAs("Files from local commits should not exist after reset").isTrue()
  }

  @Test
  fun `test non conflicting local changes persist after reset update`(): Unit = with(context) {
    commitAndPushFromBro()

    val localFiles = listOf(file("local1.txt"), file("local2.txt"))
    localFiles.forEach { it.create() }

    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)

    assertThat(localFiles.all { it.exists() }).describedAs("Locally changed uncommited files should exist after reset").isTrue()
  }

  @Test
  fun `test stash is called for reset update and merge dialog is shown if there are conflicting tracked local changes`(): Unit = with(context) {
    commitAndPushFromBro()
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
    assertThat(stashCalled).describedAs("Stash should have been called for dirty working tree").isTrue()
    assertThat(vcsHelper.mergeDialogWasShown()).isTrue()
    repo.assertStatus(localFile, 'M')
  }

  @Test
  fun `test update range on reset update for diverged branches`(): Unit = with(context) {
    commitAndPushFromBro()

    updateWith(REBASE) // fast-forward

    val before = last().asHash()
    repeat(3) {
      commitAndPushFromBro()
    }
    cd(broRepo)
    val after = last().asHash()

    cd(repo)

    commitLocalFilesToRepo()

    val (_, updateProcess) = updateWith(RESET)

    assertThat(getUpdatedRange(updateProcess)).describedAs("Updated range is incorrect").isEqualTo(HashRange(before, after))
  }

  @Test
  fun `test reset update when remote branch is not set`(): Unit = with(context) {
    repeat(3) {
      commitAndPushFromBro()
    }

    cd(repo)
    git("branch --unset-upstream master")

    updateWith(RESET)

    assertErrorNotification(GitBundle.message("update.notification.update.error"),
                            GitUpdateProcess.getNoTrackedBranchError(repo, "master"))
  }

  @Test
  fun `test reset update when remote branch is deleted`(): Unit = with(context) {
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

  @Test
  fun `test reset drops local commits when remote branch has no new commits`(): Unit = with(context) {
    cd(repo)
    val before = last()

    commitLocalFilesToRepo()

    assertNotEquals(before, last())

    val (result, _) = updateWith(RESET)
    assertSuccessfulUpdate(result)

    assertThat(last()).isEqualTo(before)
  }

  private fun getUpdatedRange(updateProcess: GitUpdateProcess): HashRange {
    return requireNotNull(updateProcess.updatedRanges)[repo].let(::requireNotNull)
  }

  private fun GitPlatformTestContext.updateWith(method: UpdateMethod): Pair<GitUpdateResult, GitUpdateProcess> =
    runUnderProgress { indicator ->
      val process = GitUpdateProcess(project, indicator, listOf(repo), UpdatedFiles.create(), null, false, true)
      process.update(method) to process
    }

  private fun GitPlatformTestContext.commitAndPushFromBro() {
    cd(broRepo)
    commitSomethingToBroRepo()
    git("push -u origin master")
    cd(repo)
  }

  private fun GitPlatformTestContext.commitSomethingToBroRepo() {
    cd(broRepo)
    modify("bro.txt")
  }

  private fun commitLocalFilesToRepo(): List<TestFile> {
    cd(repo)
    return listOf(file("local1.txt"), file("local2.txt")).map { it.create().addCommit("local commit") }
  }

  private fun file(path: String) = repo.file(path)

  private fun String.asHash() = HashImpl.build(this)

  private fun assertSuccessfulUpdate(result: GitUpdateResult) {
    assertThat(result).describedAs("Incorrect update result").isEqualTo(GitUpdateResult.SUCCESS)
  }
}
