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
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.repo.GitRepository
import git4idea.test.*
import org.junit.Assume.assumeTrue
import java.io.File

class GitMultiRepoUpdateTest : GitUpdateBaseTest() {

  private lateinit var repository: GitRepository
  private lateinit var community: GitRepository
  private lateinit var bro: File
  private lateinit var bromunity: File

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

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)

    assertEquals("Update result is incorrect", GitUpdateResult.NOT_READY, result)
    assertErrorNotification("Can't Update", GitUpdateProcess.getNoTrackedBranchError(community, "feature"))
  }

  private fun updateWithMerge(): GitUpdateResult {
    return GitUpdateProcess(project, EmptyProgressIndicator(), repositories(), UpdatedFiles.create(), false, true).update(UpdateMethod.MERGE)
  }

  private fun repositories() = listOf(repository, community)
}