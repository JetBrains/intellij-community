// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.branch.GitBranchUtil
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.initRepo
import git4idea.test.last
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
internal class GitStandardWorkTreeTest : GitWorkTreeBaseTest() {

  override fun GitPlatformTestContext.initMainRepo(): Path {
    val mainDir = testNioRoot.resolve("main")
    Files.createDirectories(mainDir)
    initRepo(project, mainDir, true)
    return mainDir
  }

  @Test
  fun `test local branches`(): Unit = with(context) {
    cd(myMainRoot)
    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")

    myRepo.update()

    val branches = myRepo.branches
    // 'project' is created automatically by `git worktree add`
    assertThat(branches.localBranches.map { it.name })
      .describedAs("Local branches are identified incorrectly")
      .containsExactlyInAnyOrder("master", "feature", "project")
    assertBranchHash(masterHead, branches, "master")
    assertBranchHash(featureHead, branches, "feature")
  }

  @Test
  fun `test remote branches`(): Unit = with(context) {
    setUpRemote()

    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    git("push origin feature")

    myRepo.update()

    val branches = myRepo.branches
    assertThat(branches.remoteBranches.map { it.nameForLocalOperations })
      .describedAs("Remote branches are identified incorrectly")
      .containsExactlyInAnyOrder("origin/master", "origin/feature")
    assertBranchHash(masterHead, branches, "origin/master")
    assertBranchHash(featureHead, branches, "origin/feature")
  }

  @Test
  fun `test HEAD`(): Unit = with(context) {
    cd(myRepo)
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    myRepo.update()

    assertThat(myRepo.currentBranchName).describedAs("Incorrect current branch").isEqualTo("feature")
    assertThat(myRepo.currentRevision).describedAs("Incorrect current revision").isEqualTo(featureHead)
  }

  @Test
  fun `test tracked branch`(): Unit = with(context) {
    setUpRemote()

    myRepo.update()

    val masterBranch = myRepo.branches.findLocalBranch("master")!!
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(myRepo, masterBranch)!!
    assertThat(trackInfo.remoteBranch.nameForLocalOperations).isEqualTo("origin/master")
  }

  private fun GitPlatformTestContext.setUpRemote(): String {
    cd(testNioRoot)
    git("clone --bare $myMainRoot parent.git")
    cd(myMainRoot)
    val parentPath = testNioRoot.resolve("parent.git").toString()
    git("remote add origin $parentPath")
    git("push origin -u master")
    return parentPath
  }
}
