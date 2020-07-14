// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.openapi.vcs.Executor.cd
import git4idea.branch.GitBranchUtil
import git4idea.test.*
import java.nio.file.Files
import java.nio.file.Path

class GitStandardWorkTreeTest : GitWorkTreeBaseTest() {
  override fun initMainRepo() : Path {
    val mainDir = testNioRoot.resolve("main")
    Files.createDirectories(mainDir)
    initRepo(project, mainDir, true)
    return mainDir
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
    val parentPath = testNioRoot.resolve("parent.git").toString()
    git("remote add origin $parentPath")
    git("push origin -u master")
    return parentPath
  }
}