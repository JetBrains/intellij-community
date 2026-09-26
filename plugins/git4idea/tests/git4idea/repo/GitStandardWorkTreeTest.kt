// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.branch.GitBranchUtil
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.initRepo
import git4idea.test.last
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
internal class GitStandardWorkTreeTest {
  private val contextFixture = gitPlatformContextFixture().gitWorkTreeFixture {
    val mainDir = testNioRoot.resolve("main")
    Files.createDirectories(mainDir)
    initRepo(project, mainDir, true)
    mainDir
  }
  private val context: GitWorkTreeContext get() = contextFixture.get()

  @Test
  fun `test local branches`(): Unit = with(context) {
    cd(mainRoot)
    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")

    repo.update()

    val branches = repo.branches
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

    repo.update()

    val branches = repo.branches
    assertThat(branches.remoteBranches.map { it.nameForLocalOperations })
      .describedAs("Remote branches are identified incorrectly")
      .containsExactlyInAnyOrder("origin/master", "origin/feature")
    assertBranchHash(masterHead, branches, "origin/master")
    assertBranchHash(featureHead, branches, "origin/feature")
  }

  @Test
  fun `test HEAD`(): Unit = with(context) {
    cd(repo)
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    repo.update()

    assertThat(repo.currentBranchName).describedAs("Incorrect current branch").isEqualTo("feature")
    assertThat(repo.currentRevision).describedAs("Incorrect current revision").isEqualTo(featureHead)
  }

  @Test
  fun `test tracked branch`(): Unit = with(context) {
    setUpRemote()

    repo.update()

    val masterBranch = repo.branches.findLocalBranch("master")!!
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, masterBranch)!!
    assertThat(trackInfo.remoteBranch.nameForLocalOperations).isEqualTo("origin/master")
  }

  private fun GitWorkTreeContext.setUpRemote(): String {
    cd(testNioRoot)
    git("clone --bare $mainRoot parent.git")
    cd(mainRoot)
    val parentPath = testNioRoot.resolve("parent.git").toString()
    git("remote add origin $parentPath")
    git("push origin -u master")
    return parentPath
  }
}
