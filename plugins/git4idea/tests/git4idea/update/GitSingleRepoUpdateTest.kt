// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.config.UpdateMethod
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

  private fun updateWithRebase(): GitUpdateResult {
    return GitUpdateProcess(project, EmptyProgressIndicator(), listOf(repo), UpdatedFiles.create(), false, true).update(UpdateMethod.REBASE)
  }

  private fun File.commitAndPush() {
    cd(this)
    tac("f.txt")
    git("push -u origin master")
    cd(repo)
  }

  private fun assertSuccessfulUpdate(result: GitUpdateResult) {
    assertEquals("Incorrect update result", GitUpdateResult.SUCCESS, result)
  }

  internal fun file(path: String) = repo.file(path)
}