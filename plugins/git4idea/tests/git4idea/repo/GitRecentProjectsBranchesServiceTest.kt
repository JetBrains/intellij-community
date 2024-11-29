package git4idea.repo

import com.intellij.testFramework.utils.io.createDirectory
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import kotlinx.coroutines.runBlocking
import kotlin.io.path.pathString

class GitRecentProjectsBranchesServiceTest : GitSingleRepoTest() {
  fun `test project path is sln file`() {
    val slnPath = projectRoot.createFile("test.sln").path

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, slnPath) }
    val expected = masterBranch()
    assertEquals(expected, actual)
  }

  fun `test project path is inside git repo`() {
    val projectPath = projectRoot.toNioPath().createDirectory("1/2/3/4/5").pathString

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath) }
    val expected = masterBranch()
    assertEquals(expected, actual)
  }

  fun `test project path is in git worktree`() {
    val worktree = "test"
    git("worktree add $worktree")

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, "$projectPath/$worktree") }
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = worktree, headFilePath = "${repo.repositoryFiles.worktreesDirFile}/$worktree/HEAD"),
      actual
    )
  }

  fun `test branch is unknown if detached HEAD`() {
    makeCommit("1")
    git("checkout HEAD^")

    val actual = runBlocking { GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath) }
    assertEquals(notOnBranch(), actual)
  }

  fun `test unknown value is recalculated`() {
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(notOnBranch(), projectPath)
    }

    assertEquals(masterBranch(), actual)
  }

  fun `test not reloaded if not a git repository`() {
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(GitRecentProjectCachedBranch.Unknown, projectPath)
    }
    assertEquals(GitRecentProjectCachedBranch.Unknown, actual)
  }

  private fun notOnBranch() = GitRecentProjectCachedBranch.NotOnBranch(repo.repositoryFiles.headFile.path)

  private fun masterBranch(): GitRecentProjectCachedBranch.KnownBranch =
    GitRecentProjectCachedBranch.KnownBranch(
      branchName = "master",
      headFilePath = repo.repositoryFiles.headFile.path
    )
}