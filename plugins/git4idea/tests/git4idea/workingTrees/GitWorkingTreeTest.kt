// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitBranch
import git4idea.GitWorkingTree
import git4idea.repo.GitLinkedWorktreeTest
import git4idea.repo.GitRepository
import git4idea.test.GitSingleRepoTest
import git4idea.test.registerRepo
import java.nio.file.Path

abstract class GitWorkingTreeTest : GitSingleRepoTest() {

  class GitWorkingTreeOnMainRepoTest : GitWorkingTreeTest() {
    override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
      return listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
    }
  }


  class GitWorkingTreeOnLinkedWorkingTreeTest : GitWorkingTreeTest() {
    val branchName = "feature"
    val mainRepoRelativePath = "mainRepo"

    val mainRepoPath: Path
      get() = testNioRoot.resolve(mainRepoRelativePath)

    override fun doCreateAndOpenProject(): Project {
      val projectRootPath = getProjectDirOrFile(true)
      return GitLinkedWorktreeTest.setUpProjectAndWorkingTree(testNioRoot, projectRootPath, mainRepoRelativePath, branchName)
    }

    override fun createRepository(): GitRepository {
      return registerRepo(project, projectNioRoot)
    }

    override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
      return listOf(
        GitWorkingTree(mainRepoPath.toString(), "refs/heads/master", true, false),
        GitWorkingTree(repo.root.path, GitBranch.REFS_HEADS_PREFIX + branchName, false, true),
      )
    }
  }

  abstract fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree>


  fun `test listing working trees`() {
    val trees = listTrees()
    val expected = getExpectedDefaultWorkingTrees()
    assertSameElements(trees, expected)
  }

  fun `test deleting working tree`() {
    val branch = "tree"
    val treeRoot = "treeRoot"
    val newWorkingTreeRootPath = testNioRoot.resolve(treeRoot)

    git("worktree add -B $branch ../$treeRoot")

    val createdWorkTreeRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)
    assertNotNull(createdWorkTreeRoot)

    repo.workingTreeHolder.ensureUpToDateForTests()
    val createdWorkingTrees = repo.workingTreeHolder.getWorkingTrees()
    val workingTree = createdWorkingTrees.firstOrNull { it.path.path.endsWith(treeRoot) }
    assertNotNull(workingTree)

    GitWorkingTreesCommandService.getInstance().deleteWorkingTree(project, workingTree!!)
    val removedWorkingTree = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)
    assertNull(removedWorkingTree)

    repo.workingTreeHolder.ensureUpToDateForTests()
    assertSameElements(repo.workingTreeHolder.getWorkingTrees(), getExpectedDefaultWorkingTrees())
  }

  fun listTrees(): List<GitWorkingTree> {
    val listener = GitListWorktreeLineListener(repo)
    val commandResult = GitWorkingTreesCommandService.getInstance().listWorktrees(repo, listener)
    commandResult.throwOnError()
    return listener.trees
  }

}