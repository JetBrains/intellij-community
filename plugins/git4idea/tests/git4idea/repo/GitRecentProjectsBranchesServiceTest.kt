// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.createDirectory
import git4idea.test.GitSingleRepoContext
import git4idea.test.createFile
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.pathString

@TestApplication
internal class GitRecentProjectsBranchesServiceTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test project path is sln file`(): Unit = with(context) {
    val slnPath = createFile(projectRoot, "test.sln").path

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, slnPath) }
    assertThat(actual).isEqualTo(masterBranch())
  }

  @Test
  fun `test project path is inside git repo`(): Unit = with(context) {
    val nestedProjectPath = projectRoot.toNioPath().createDirectory("1/2/3/4/5").pathString

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, nestedProjectPath) }
    assertThat(actual).isEqualTo(masterBranch())
  }

  @Test
  fun `test project path is in git worktree`(): Unit = with(context) {
    val worktree = "test"
    git("worktree add $worktree")

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, "$projectPath/$worktree") }
    val expectedPath = repo.repositoryFiles.worktreesDirFile.resolve(worktree).resolve("HEAD").toString()
    assertThat(actual).isEqualTo(
      GitRecentProjectCachedBranch.KnownBranch(worktree, expectedPath)
    )
  }

  @Test
  fun `test branch is unknown if detached HEAD`(): Unit = with(context) {
    makeCommit("1")
    git("checkout HEAD^")

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath) }
    assertThat(actual).isEqualTo(notOnBranch())
  }

  @Test
  fun `test unknown value is recalculated`(): Unit = with(context) {
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(notOnBranch(), projectPath)
    }

    assertThat(actual).isEqualTo(masterBranch())
  }

  @Test
  fun `test not reloaded if not a git repository`(): Unit = with(context) {
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(GitRecentProjectCachedBranch.Unknown, projectPath)
    }
    assertThat(actual).isEqualTo(GitRecentProjectCachedBranch.Unknown)
  }
}

private fun GitSingleRepoContext.notOnBranch() = GitRecentProjectCachedBranch.NotOnBranch(repo.repositoryFiles.headFile.path)

private fun GitSingleRepoContext.masterBranch(): GitRecentProjectCachedBranch.KnownBranch =
  GitRecentProjectCachedBranch.KnownBranch(
    branchName = "master",
    headFilePath = repo.repositoryFiles.headFile.path
  )
