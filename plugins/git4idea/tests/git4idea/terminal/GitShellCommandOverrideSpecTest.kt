// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.terminal

import com.intellij.openapi.vcs.FilePath
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchesCollection
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.mockito.Mockito.*

class GitShellCommandOverrideSpecTest : BasePlatformTestCase() {
  private data class MultiTestFixture(
    val fixture: ShellCompletionTestFixture,
    val assertions: SoftAssertions = SoftAssertions()
  ) {
    fun finish() = assertions.assertAll()
  }

  private val commandsThatShouldCompleteWithLocalBranches = listOf(
    "git rebase origin ",
    "git fetch ",
    "git fetch origin ",
    "git push ", // Should not happen, but there's no way to indicate optional + ordered args
    "git push origin ",
    "git pull ",
    "git pull origin ",
    "git stash branch ",
    "git branch -d ",
    "git branch --track ",
    "git branch --no-track ",
    "git branch --no-track main ",
    "git branch -m ",
    "git branch -m main ",
    "git branch -M ",
    "git branch -M main ",
    "git branch --edit-description ",
    "git branch --unset-upstream ",
    "git switch "
  )

  fun `test command completions contain local branches for repository-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitRepositoryFixture())
    commandsThatShouldCompleteWithLocalBranches.forEach { test.localBranchesCompleteFor(it) }
    test.finish()
  }

  fun `test command completions contain local branches for commandline-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitCommandLineFixture())
    commandsThatShouldCompleteWithLocalBranches.forEach { test.localBranchesCompleteFor(it) }
    test.finish()
  }

  // Ignored, needs context changes to be able to find -r/--remotes in prefix
  fun `_test command completions contain remote branches`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitRepositoryFixture())
    test.remoteBranchesCompleteFor("git branch -D -r ")
    test.remoteBranchesCompleteFor("git branch -d --remotes ")
    test.finish()
  }

  private val commandsThatShouldCompleteWithAllBranches = listOf(
    "git diff main ",
    "git diff ",
    "git reset ",
    "git reset main ",
    "git rebase ",
    "git branch -u ",
    "git branch --set-upstream-to ",
    "git checkout ",
    "git merge ",
  )

  fun `test command completions contain all branches for repository-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitRepositoryFixture())
    commandsThatShouldCompleteWithAllBranches.forEach { test.allBranchesCompleteFor(it) }
    test.finish()
  }

  fun `test command completions contain all branches for commandline-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitCommandLineFixture())
    commandsThatShouldCompleteWithAllBranches.forEach { test.allBranchesCompleteFor(it) }
    test.finish()
  }

  private val commandsThatShouldCompleteWithRemotes = listOf(
    "git rebase ",
    "git push ",
    "git pull ",
    "git pull --rebase ", // iffy, what does a remote here mean?
    "git pull main --rebase ",
    "git remote rm ",
    "git remote rename ",
    "git fetch ",
  )

  fun `test command completions contain all remotes for repository-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitRepositoryFixture())
    commandsThatShouldCompleteWithRemotes.forEach { test.remotesCompleteFor(it) }
    test.finish()
  }

  fun `test command completions contain all remotes for commandline-based generator`(): Unit = runBlocking {
    val test = MultiTestFixture(createGitCommandLineFixture())
    commandsThatShouldCompleteWithRemotes.forEach { test.remotesCompleteFor(it) }
    test.finish()
  }

  // depends on `test command completions contain local branches` to pass
  fun `test current branch is a higher priority completion than others`(): Unit = runBlocking {
    val fixture = createGitRepositoryFixture(localBranches = ALL_LOCAL_BRANCHES, currentBranch = BRANCH_C)
    val completions = fixture.getCompletions("git branch -d ")

    val currentBranchCompletion = completions.find { it.name == BRANCH_C.name }
    val otherBranchCompletions = completions.filter { it.name != BRANCH_C.name }

    assertThat(currentBranchCompletion?.priority)
      .isNotNull().isGreaterThan(otherBranchCompletions.maxOf { it.priority })
  }

  private suspend fun MultiTestFixture.localBranchesCompleteFor(forCommand: String,
                                                                expectedBranches: List<GitLocalBranch> = ALL_LOCAL_BRANCHES,
                                                                notExpectedBranches: List<GitRemoteBranch> = ALL_REMOTE_BRANCHES) {
    val completions = fixture.getCompletions(forCommand)
    assertions.assertThat(completions)
      .withFailMessage { "Expected local branches to be completed for: '${forCommand}'" }
      .map<String> { it.name }.containsAll(expectedBranches.map { it.name })

    assertions.assertThat(completions)
      .withFailMessage { "Expected remote branches to *not* be completed for: '${forCommand}'" }
      .map<String> { it.name }.doesNotContainAnyElementsOf(notExpectedBranches.map { it.name })
  }

  private suspend fun MultiTestFixture.remoteBranchesCompleteFor(forCommand: String,
                                                                 expectedBranches: List<GitRemoteBranch> = ALL_REMOTE_BRANCHES,
                                                                 notExpectedBranches: List<GitLocalBranch> = ALL_LOCAL_BRANCHES) {
    val completions = fixture.getCompletions(forCommand)
    assertions.assertThat(completions)
      .withFailMessage { "Expected remote branches to be completed for: '${forCommand}'" }
      .map<String> { it.name }.containsAll(expectedBranches.map { it.name })

    assertions.assertThat(completions)
      .withFailMessage { "Expected local branches to *not* be completed for: '${forCommand}'" }
      .map<String> { it.name }.doesNotContainAnyElementsOf(notExpectedBranches.map { it.name })
  }

  private suspend fun MultiTestFixture.allBranchesCompleteFor(forCommand: String,
                                                              expectedBranches: List<GitBranch> = ALL_LOCAL_BRANCHES + ALL_REMOTE_BRANCHES) {
    assertions.assertThat(fixture.getCompletions(forCommand))
      .withFailMessage { "Expected all branches to be completed for: '${forCommand}'" }
      .map<String> { it.name }.containsAll(expectedBranches.map { it.name })
  }

  private suspend fun MultiTestFixture.remotesCompleteFor(forCommand: String,
                                                          expectedRemotes: List<GitRemote> = ALL_REMOTES) {
    assertions.assertThat(fixture.getCompletions(forCommand))
      .withFailMessage { "Expected remotes to be completed for: '${forCommand}'" }
      .map<String> { it.name }.containsAll(expectedRemotes.map { it.name })
  }

  private fun createGitCommandLineFixture(
    currentBranch: GitLocalBranch = MAIN,
    localBranches: List<GitLocalBranch> = ALL_LOCAL_BRANCHES,
    remoteBranches: List<GitRemoteBranch> = ALL_REMOTE_BRANCHES,
    remotes: List<GitRemote> = ALL_REMOTES
  ): ShellCompletionTestFixture = ShellCompletionTestFixture.builder(project)
    .mockShellCommandResults { command ->
      if (command.startsWith(GET_DIRECTORY_FILES.functionName)) {
        return@mockShellCommandResults ShellCommandResult.create("file1\nfile2", exitCode = 0)
      }
      if (!command.startsWith("git")) error("Unknown command: $command")
      when (command) {
        GET_LOCAL_BRANCHES_COMMAND ->
          ShellCommandResult.create(localBranches.joinToString("\n") { it.toCommandResult(currentBranch) }, exitCode = 0)
        GET_REMOTE_BRANCHES_COMMAND ->
          ShellCommandResult.create(remoteBranches.joinToString("\n") { it.toCommandResult(currentBranch) }, exitCode = 0)
        GET_ALL_BRANCHES_COMMAND ->
          ShellCommandResult.create((localBranches + remoteBranches).joinToString("\n") { it.toCommandResult(currentBranch) }, exitCode = 0)
        GET_REMOTES_COMMAND ->
          ShellCommandResult.create(remotes.joinToString("\n") {
            (it.urls.map { url -> "${it.name}\t${url} (fetch)" } +
             it.pushUrls.map { url -> "${it.name}\t${url} (push)" })
              .joinToString("\n")
          }, exitCode = 0)
        GET_ALIASES_COMMAND ->
          ShellCommandResult.create("", exitCode = 0)
        else -> error("Unknown command: $command")
      }
    }
    .build()

  private fun createGitRepositoryFixture(
    currentBranch: GitLocalBranch = MAIN,
    localBranches: List<GitLocalBranch> = ALL_LOCAL_BRANCHES,
    remoteBranches: List<GitRemoteBranch> = ALL_REMOTE_BRANCHES,
    remotes: List<GitRemote> = ALL_REMOTES,
    branchToHash: (GitBranch) -> Hash = { HashImpl.build("abcdef1234567890") }
  ): ShellCompletionTestFixture {
    val repository = mock<GitRepository>()
    `when`(repository.branches).thenReturn(GitBranchesCollection(
      localBranches.associateWith(branchToHash),
      remoteBranches.associateWith(branchToHash),
      listOf()
    ))
    `when`(repository.remotes).thenReturn(remotes)
    `when`(repository.currentBranch).thenReturn(currentBranch)

    val repositoryManager = mock<GitRepositoryManager>()
    `when`(repositoryManager.getRepositoryForFileQuick(any<FilePath>())).thenReturn(repository)
    project.replaceService(GitRepositoryManager::class.java, repositoryManager, testRootDisposable)

    val fixture = ShellCompletionTestFixture.builder(project)
      .mockShellCommandResults { command ->
        if (command.startsWith("__jetbrains_intellij_get_directory_files")) {
          return@mockShellCommandResults ShellCommandResult.create("file1\nfile2", exitCode = 0)
        }
        if (!command.startsWith("git")) error("Unknown command: $command")
        when (command) {
          GET_ALIASES_COMMAND ->
            ShellCommandResult.create("", exitCode = 0)
          else -> error("Unknown command: $command")
        }
      }
      .build()

    return fixture
  }

  private fun GitBranch.toCommandResult(currentBranch: GitLocalBranch): String =
    when (this) {
      is GitRemoteBranch -> "remotes/${name}$COLUMN_SPLIT_CHARACTER"
      is GitLocalBranch -> "heads/${name}$COLUMN_SPLIT_CHARACTER" + if (this == currentBranch) "*" else ""
      else -> error("unknown branch type: $this")
    }

  companion object {
    private val ORIGIN = GitRemote("origin", listOf("https://origin.com/some/repo"), listOf("https://origin.com/some/repo"), listOf(),
                                   listOf())
    private val GITHUB = GitRemote("github", listOf("https://github.com/some/repo"), listOf("https://github.com/some/repo"), listOf(),
                                   listOf())
    private val GITLAB = GitRemote("gitlab", listOf("https://gitlab.com/some/repo"), listOf("https://gitlab.com/some/repo"), listOf(),
                                   listOf())

    private val MAIN = GitLocalBranch("main")
    private val ORIGIN_MAIN = GitStandardRemoteBranch(ORIGIN, "main")

    private val BRANCH_A = GitLocalBranch("a")
    private val BRANCH_B = GitLocalBranch("cl/feature/1")
    private val BRANCH_C = GitLocalBranch("branch-1234")

    private val ORIGIN_BRANCH_A = GitStandardRemoteBranch(ORIGIN, BRANCH_A.name)
    private val GITHUB_BRANCH_B = GitStandardRemoteBranch(GITHUB, BRANCH_B.name)
    private val GITLAB_BRANCH_A = GitStandardRemoteBranch(GITLAB, BRANCH_A.name)

    private val ALL_LOCAL_BRANCHES = listOf(
      MAIN, BRANCH_A, BRANCH_B, BRANCH_C
    )

    private val ALL_REMOTE_BRANCHES = listOf(
      ORIGIN_MAIN, ORIGIN_BRANCH_A, GITHUB_BRANCH_B, GITLAB_BRANCH_A
    )

    private val ALL_REMOTES = listOf(
      ORIGIN, GITHUB, GITLAB
    )
  }
}