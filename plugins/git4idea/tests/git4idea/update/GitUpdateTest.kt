/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.update

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.config.GitVcsSettings.UpdateChangesPolicy
import git4idea.config.GitVcsSettings.UpdateChangesPolicy.STASH
import git4idea.config.UpdateMethod
import git4idea.test.*
import java.io.File

class GitUpdateTest : GitSingleRepoTest() {

  private lateinit var broRepo : File
  private lateinit var originalPreservingPolicy : UpdateChangesPolicy

  override fun setUp() {
    super.setUp()

    originalPreservingPolicy = myGitSettings.updateChangesPolicy()
    myGitSettings.setUpdateChangesPolicy(STASH)

    val parent = prepareRemoteRepo(myRepo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    myRepo.update()
  }

  override fun tearDown() {
    try {
      myGitSettings.setUpdateChangesPolicy(originalPreservingPolicy)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#git4idea.update")

  fun `test stash is called for rebase if there are local changes and local commits`() {
    broRepo.commitAndPush()
    tac("a.txt")
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    myGit.stashListener = {
      stashCalled = true
    }

    val result = updateWithRebase()
    assertSuccessfulUpdate(result)
    assertTrue("Stash should have been called for dirty working tree", stashCalled)
    myRepo.assertStatus(localFile, 'M')
  }

  // "Fast-forward merge" optimization
  fun `test stash is not called for rebase if there are local changes, but no local commits`() {
    broRepo.commitAndPush()
    val localFile = file("a.txt").append("content").add().file
    updateChangeListManager()

    var stashCalled = false
    myGit.stashListener = {
      stashCalled = true
    }

    val result = updateWithRebase()
    assertSuccessfulUpdate(result)
    assertFalse("Stash shouldn't be called, because of fast-forward merge optimization", stashCalled)
    myRepo.assertStatus(localFile, 'A')
  }

  private fun createBroRepo(broName: String, parentRepo: File): File {
    cd(myTestRoot)
    git("clone " + parentRepo.name + " " + broName)
    return File(myTestRoot, broName)
  }

  private fun updateWithRebase(): GitUpdateResult {
    return GitUpdateProcess(myProject, EmptyProgressIndicator(), listOf(myRepo), UpdatedFiles.create(), false).update(UpdateMethod.REBASE)
  }

  private fun File.commitAndPush() {
    cd(this)
    tac("f.txt")
    git("push -u origin master")
    cd(myRepo)
  }

  private fun assertSuccessfulUpdate(result: GitUpdateResult) {
    assertEquals("Incorrect update result", GitUpdateResult.SUCCESS, result)
  }
}