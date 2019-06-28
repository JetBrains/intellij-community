// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.vcs.log.impl.HashImpl
import git4idea.config.UpdateMethod.REBASE
import git4idea.repo.GitRepository
import git4idea.test.*
import java.io.File

class GitSingleRepoUpdateTest : GitUpdateBaseTest() {

  private lateinit var repo: GitRepository
  private lateinit var broRepo : File

  override fun setUp() {
    super.setUp()

    repo = createRepository(project, projectPath, true)
    cd(projectPath)

    val parent = prepareRemoteRepo(repo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    repo.update()
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#git4idea.update")

  fun `test stash is called for rebase if there are local changes and local commits`() {
    broRepo.commitAndPush()
    tac("a.txt")
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val result = updateWithRebase()
    assertSuccessfulUpdate(result)
    assertTrue("Stash should have been called for dirty working tree", stashCalled)
    repo.assertStatus(localFile, 'M')
  }

  // "Fast-forward merge" optimization
  fun `test stash is not called for rebase if there are local changes, but no local commits`() {
    broRepo.commitAndPush()
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    val result = updateWithRebase()
    assertSuccessfulUpdate(result)
    assertFalse("Stash shouldn't be called, because of fast-forward merge optimization", stashCalled)
    repo.assertStatus(localFile, 'A')
  }

  // IDEA-167688
  fun `test stash is not called for rebase if there are no local changes`() {
    broRepo.commitAndPush()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    updateWithRebase()
    assertFalse("Stash shouldn't be called for clean working tree", stashCalled)
  }

  // IDEA-76760
  fun `test stash is called for rebase in case of AD changes`() {
    broRepo.commitAndPush()

    var stashCalled = false
    git.stashListener = {
      stashCalled = true
    }

    cd(repo)
    val file = file("a.txt").create().add().delete().file
    updateChangeListManager()

    val result = updateWithRebase()
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

    val updateProcess = updateProcess()
    updateProcess.update(REBASE)

    val range = updateProcess.updatedRanges!![repo]!!
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

    val updateProcess = updateProcess()
    updateProcess.update(REBASE)

    val range = updateProcess.updatedRanges!![repo]!!
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

    val updateProcess = updateProcess()
    updateProcess.update(REBASE)

    val range = updateProcess.updatedRanges!![repo]!!
    assertEquals("Updated range is incorrect", HashRange(before, after), range)
  }

  private fun updateWithRebase() = updateProcess().update(REBASE)

  private fun updateProcess() = GitUpdateProcess(project, EmptyProgressIndicator(), listOf(repo), UpdatedFiles.create(), false, true)

  private fun File.commitAndPush() {
    cd(this)
    commitSomethingToBroRepo()
    git("push -u origin master")
    cd(repo)
  }

  private fun commitSomethingToBroRepo() {
    cd(broRepo)
    val file = File(broRepo, "bro.txt")
    val content = "content-${Math.random()}\n"
    FileUtil.writeToFile(file, content.toByteArray(), true)
    addCommit("modified bro.txt")
  }

  private fun String.asHash() = HashImpl.build(this)

  private fun assertSuccessfulUpdate(result: GitUpdateResult) {
    assertEquals("Incorrect update result", GitUpdateResult.SUCCESS, result)
  }

  internal fun file(path: String) = repo.file(path)
}