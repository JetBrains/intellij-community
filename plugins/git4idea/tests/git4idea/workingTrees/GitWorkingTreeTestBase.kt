// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.repo.GitRepository
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.registerRepo
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import java.nio.file.Paths

internal abstract class GitWorkingTreeTestBase(private val contextFixture: TestFixture<GitSingleRepoContext>) {

  protected val context: GitSingleRepoContext get() = contextFixture.get()
  protected val repo: GitRepository get() = context.repo

  abstract fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree>
  abstract val mainRepoPath: Path

  protected fun doTestWorkingTreeCreation(
    data: GitWorkingTreeDialogData,
    expectedWorkingTree: GitWorkingTree,
    expectedWorkingTreeBranchName: String?,
    expectedWorkingTreeLastCommit: String,
  ): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)
    holder.expectEvent(
      { repo.ensureWorkingTreesUpToDateForTests() },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED }
    )

    assertThat(repo.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(getExpectedDefaultWorkingTrees())

    holder.expectEvent(
      {
        val result = GitWorkingTreesService.getInstance(project).createWorkingTree(repo, data)
        assertThat(result.success).describedAs(result.errorOutputAsHtmlString).isTrue()
        val worktreesDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mainRepoPath.resolve(".git/worktrees"))
        refresh(worktreesDir!!)
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED }
    )

    val workingTrees = repo.workingTreeHolder.getWorkingTrees()
    val expected = getExpectedDefaultWorkingTrees() + expectedWorkingTree

    assertThat(workingTrees).containsExactlyInAnyOrderElementsOf(expected)

    val workingTreeRepo = registerRepo(project, Paths.get(data.workingTreePath.path))
    assertThat(workingTreeRepo.currentBranchName)
      .describedAs("Current branch of the created working tree is incorrect")
      .isEqualTo(expectedWorkingTreeBranchName)
    assertThat(workingTreeRepo.currentRevision)
      .describedAs("Last commit of the created working tree is incorrect")
      .isEqualTo(expectedWorkingTreeLastCommit)
  }
}
