// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitLocalBranch
import git4idea.GitWorkingTree
import git4idea.repo.GitRepository
import git4idea.test.GitSingleRepoTest
import git4idea.test.branch
import git4idea.test.registerRepo
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import kotlin.io.path.pathString

class GitCreateWorkingTreeTest : GitSingleRepoTest() {

  fun `test listing detached working tree`() {
    val treeRoot = "rootOfTree"
    val branch = "branch"
    val branchParameterForWorkingTree = "refs/heads/branch"

    createBranch(repo, branch)
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    val workingTreePath = LocalFilePath(testNioRoot.resolve(treeRoot).toString(), true)
    val output = git("worktree add ../$treeRoot $branchParameterForWorkingTree")
    assertTrue(output.contains("Preparing worktree (detached HEAD"))

    val workingTreeRepo = registerRepo(project, Paths.get(workingTreePath.path))
    assertNull("Current branch is should be null, got ${workingTreeRepo.currentBranchName} instead",
               workingTreeRepo.currentBranchName)

    repo.workingTreeHolder.ensureUpToDateForTests()
    val workingTrees = repo.workingTreeHolder.getWorkingTrees()
    val expected = listOf(
      GitWorkingTree(repo.root.path,
                     repo.currentBranch!!.fullName,
                     true, true),
      GitWorkingTree("${testNioRoot.pathString}/$treeRoot",
                     null,
                     false, false)
    )

    assertSameElements(workingTrees, expected)
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