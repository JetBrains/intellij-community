// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.vcs.Executor.cd
import git4idea.config.GitIncomingRemoteCheckStrategy
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.tac
import java.nio.file.Path

internal class GitBranchIncomingOutgoingManagerTest : GitPlatformTest() {
  private lateinit var repo: GitRepository
  private lateinit var broRepo: Path
  private lateinit var manager: GitBranchIncomingOutgoingManager

  override fun setUp() {
    super.setUp()

    repo = createRepository(project, projectNioRoot.toString())
    cd(projectPath)

    val parent = prepareRemoteRepo(repo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    repo.update()

    manager = GitBranchIncomingOutgoingManager.getInstance(project)
  }

  override fun hasRemoteGitOperation() = true

  fun `test incoming and outgoing commits with fetch strategy`() {
    GitVcsSettings.getInstance(myProject).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.FETCH)

    // Create 2 commits in bro repo and push
    cd(broRepo)
    tac("a.txt")
    tac("b.txt")
    git("push origin master")

    // Create 1 local commit without pushing
    cd(repo.root)
    tac("local.txt")

    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertEquals(2, state.totalIncoming())
    assertFalse(state.hasUnfetched())
    assertEquals(1, state.totalOutgoing())
  }

  fun `test incoming and outgoing commits with ls-remote strategy`() {
    GitVcsSettings.getInstance(myProject).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.LS_REMOTE)

    // Create 2 commits in bro repo and push
    cd(broRepo)
    tac("a.txt")
    tac("b.txt")
    git("push origin master")

    // Create 1 local commit without pushing
    cd(repo.root)
    tac("local.txt")

    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    // With ls-remote, incoming count is 0 because commits aren't fetched yet, but hasIncoming should be true
    assertTrue(state.hasIncoming())
    assertTrue(state.hasUnfetched())
    assertEquals(1, state.totalOutgoing())
  }

  fun `test no incoming or outgoing when in sync`() {
    GitVcsSettings.getInstance(myProject).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.FETCH)

    // Just update without any changes
    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertFalse(state.hasIncoming())
    assertFalse(state.hasOutgoing())
  }

  fun `test incoming after manual fetch with strategy none`() {
    GitVcsSettings.getInstance(myProject).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.NONE)

    cd(broRepo)
    tac("a.txt")
    tac("b.txt")
    git("push origin master")

    // Verify no incoming before fetch
    updateIncomingOutgoing()
    val stateBefore = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertFalse(stateBefore.hasIncoming())

    // Manually fetch
    cd(repo.root)
    git("fetch")

    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertEquals(2, state.totalIncoming())
  }

  private fun updateIncomingOutgoing() {
    repo.update()
    manager.updateForTests()
  }
}
