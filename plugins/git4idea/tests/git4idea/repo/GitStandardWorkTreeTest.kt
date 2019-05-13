/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.repo

import com.intellij.openapi.vcs.Executor.cd
import git4idea.branch.GitBranchUtil
import git4idea.test.*
import java.io.File

class GitStandardWorkTreeTest : GitWorkTreeBaseTest() {

  override fun initMainRepo() : String {
    val mainDir = File(testRoot, "main")
    assertTrue(mainDir.mkdir())
    val path = mainDir.path
    initRepo(project, path, true)
    return path
  }

  fun `test local branches`() {
    cd(myMainRoot)
    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")

    myRepo.update()

    val branches = myRepo.branches
    val expectedBranches = listOf("master", "feature", "project") // 'project' is created automatically by `git worktree add`
    assertSameElements("Local branches are identified incorrectly",
        branches.localBranches.map { it.name }, expectedBranches)
    assertBranchHash(masterHead, branches, "master")
    assertBranchHash(featureHead, branches, "feature")
  }

  fun `test remote branches`() {
    setUpRemote()

    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    git("push origin feature")

    myRepo.update()

    val branches = myRepo.branches
    assertSameElements("Remote branches are identified incorrectly",
                       branches.remoteBranches.map { it.nameForLocalOperations },
                       listOf("origin/master", "origin/feature"))
    assertBranchHash(masterHead, branches, "origin/master")
    assertBranchHash(featureHead, branches, "origin/feature")
  }

  fun `test HEAD`() {
    cd(myRepo)
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    myRepo.update()

    assertEquals("Incorrect current branch", "feature", myRepo.currentBranchName)
    assertEquals("Incorrect current revision", featureHead, myRepo.currentRevision)
  }

  fun `test tracked branch`() {
    setUpRemote()

    myRepo.update()

    val masterBranch = myRepo.branches.findLocalBranch("master")!!
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(myRepo, masterBranch)!!
    assertEquals("origin/master", trackInfo.remoteBranch.nameForLocalOperations)
  }

  private fun setUpRemote(): String {
    cd(testRoot)
    git("clone --bare $myMainRoot parent.git")
    cd(myMainRoot)
    val parentPath = File(testRoot, "parent.git").path
    git("remote add origin $parentPath")
    git("push origin -u master")
    return parentPath
  }
}