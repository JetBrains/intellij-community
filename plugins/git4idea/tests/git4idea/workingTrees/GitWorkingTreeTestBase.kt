// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitLocalBranch
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.repo.GitRepository
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoTest
import git4idea.test.branch
import git4idea.test.registerRepo
import java.nio.file.Path
import java.nio.file.Paths

internal abstract class GitWorkingTreeTestBase : GitSingleRepoTest() {

  abstract fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree>
  abstract val mainRepoPath: Path

  protected fun doTestWorkingTreeCreation(
    data: GitWorkingTreeDialogData,
    expectedWorkingTree: GitWorkingTree,
    expectedWorkingTreeBranchName: String,
    expectedWorkingTreeLastCommit: String,
  ) {
    val holder = GitRepositoriesHolder.getAndInit(project)
    repo.workingTreeHolder.ensureUpToDateForTests()
    assertSameElements(repo.workingTreeHolder.getWorkingTrees(), getExpectedDefaultWorkingTrees())

    holder.expectEvent(
      {
        val result = GitWorkingTreesService.getInstance(project).createWorkingTree(repo, data)
        assertTrue(result.errorOutputAsHtmlString, result.success)
        val worktreesDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mainRepoPath.resolve(".git/worktrees"))
        refresh(worktreesDir!!)
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED }
    )

    val workingTrees = repo.workingTreeHolder.getWorkingTrees()
    val expected = getExpectedDefaultWorkingTrees().toMutableList()
    expected.add(expectedWorkingTree)

    assertSameElements(workingTrees, expected)

    val workingTreeRepo = registerRepo(project, Paths.get(data.workingTreePath.path))
    assertEquals("Current branch of the created working tree is incorrect",
                 expectedWorkingTreeBranchName,
                 workingTreeRepo.currentBranchName)
    assertEquals("Last commit of the created working tree is incorrect", expectedWorkingTreeLastCommit, workingTreeRepo.currentRevision)
  }

  companion object {
    fun createBranch(repo: GitRepository, branchName: String): GitLocalBranch {
      repo.branch(branchName)
      repo.update()
      val newBranch = repo.branches.findLocalBranch(branchName)
      assertNotNull(newBranch)
      return newBranch!!
    }
  }
}