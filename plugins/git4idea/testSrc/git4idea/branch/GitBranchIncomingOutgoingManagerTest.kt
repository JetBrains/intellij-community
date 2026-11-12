// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.testFramework.junit5.TestApplication
import git4idea.commands.GitLineHandler
import git4idea.config.GitIncomingRemoteCheckStrategy
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.createBroRepo
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.prepareRemoteRepo
import git4idea.test.tac
import java.nio.file.Path
import java.util.Collections.synchronizedList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitBranchIncomingOutgoingManagerTest {
  private lateinit var repo: GitRepository
  private lateinit var broRepo: Path
  private lateinit var manager: GitBranchIncomingOutgoingManager

  private val fixture = gitPlatformContextFixture(hasRemoteGitOperation = true)
  private val context: GitPlatformTestContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {

    repo = createRepository(project, projectNioRoot.toString())
    cd(projectPath)

    val parent = prepareRemoteRepo(project, testNioRoot, repo)
    git("push -u origin master")
    broRepo = createBroRepo(project, testNioRoot, "bro", parent)
    repo.update()

    manager = GitBranchIncomingOutgoingManager.getInstance(project)

    GitVcsApplicationSettings.getInstance().isUseCredentialHelper = false
  }

  @Test
  fun `test incoming and outgoing commits with fetch strategy`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.FETCH)

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
    assertThat(state.totalIncoming()).isEqualTo(2)
    assertThat(state.hasUnfetched()).isFalse()
    assertThat(state.totalOutgoing()).isEqualTo(1)
  }

  @Test
  fun `test incoming and outgoing commits with ls-remote strategy`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.LS_REMOTE)

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
    assertThat(state.hasIncoming()).isTrue()
    assertThat(state.hasUnfetched()).isTrue()
    assertThat(state.totalOutgoing()).isEqualTo(1)
  }

  @Test
  fun `test no incoming or outgoing when in sync`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.FETCH)

    // Just update without any changes
    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertThat(state.hasIncoming()).isFalse()
    assertThat(state.hasOutgoing()).isFalse()
  }

  @Test
  fun `test incoming after manual fetch with strategy none`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.NONE)

    cd(broRepo)
    tac("a.txt")
    tac("b.txt")
    git("push origin master")

    // Verify no incoming before fetch
    updateIncomingOutgoing()
    val stateBefore = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertThat(stateBefore.hasIncoming()).isFalse()

    // Manually fetch
    cd(repo.root)
    git("fetch")

    updateIncomingOutgoing()

    val state = manager.getIncomingOutgoingState(repo, repo.currentBranch!!)
    assertThat(state.totalIncoming()).isEqualTo(2)
  }


  @Test
  fun `test ls-remote command disables native credential helper when not previously authenticated`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.LS_REMOTE)
    GitVcsApplicationSettings.getInstance().isUseCredentialHelper = true

    val capturedHandlers = synchronizedList(mutableListOf<GitLineHandler>())
    git.runCommandListener = { capturedHandlers.add(it) }

    updateIncomingOutgoing()

    var lsRemoteHandlers = capturedHandlers.filter { "ls-remote" in it.printableCommandLine() }
    assertThat(lsRemoteHandlers).isNotEmpty()
    assertThat(lsRemoteHandlers.all { "credential.helper=" in it.printableCommandLine() })
      .describedAs("credential.helper= must appear in the ls-remote command line when the remote has not been authenticated yet")
      .isTrue()

    capturedHandlers.clear()
    updateIncomingOutgoing()

    // repo doesn't require authentication, so after first successful ls-remote
    // we will stop resetting the credential helper

    lsRemoteHandlers = capturedHandlers.filter { "ls-remote" in it.printableCommandLine() }
    assertThat(lsRemoteHandlers).isNotEmpty()
    assertThat(lsRemoteHandlers.none { "credential.helper=" in it.printableCommandLine() })
      .describedAs("credential.helper= must not appear in the ls-remote command line when the remote has been authenticated")
      .isTrue()
  }

  private fun updateIncomingOutgoing() {
    repo.update()
    manager.updateForTests()
  }
}
