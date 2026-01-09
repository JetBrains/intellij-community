// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialog
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.test.GitSingleRepoTest
import git4idea.test.registerRepo
import git4idea.workingTrees.GitWorkingTreeTestBase.Companion.createBranch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString

class GitCreateWorkingTreeTest : GitSingleRepoTest() {

  fun `test create worktree validation in non-empty directory`() {
    val workingTreeDir = "worktreeDir"
    val parent = File(repo.root.path, workingTreeDir)
    assertTrue(parent.mkdir())
    val childFile = parent.resolve("a.txt")
    assertTrue(childFile.createNewFile())

    val validationMessage = GitWorkingTreeDialog.getPathValidationMessage(repo.root.path, workingTreeDir)
    assertEquals("'${getPresentablePath(parent.path)}' is not empty", validationMessage)
  }

  fun `test create worktree validation in invalid directory`() {
    val workingTreeDir = "worktreeDir"
    val parent = File(repo.root.path, workingTreeDir)
    assertTrue(parent.createNewFile())

    val validationMessage = GitWorkingTreeDialog.getPathValidationMessage(repo.root.path, workingTreeDir)
    assertEquals("'${getPresentablePath(parent.path)}' is a file", validationMessage)
  }

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
    val handler = GitLineHandler(repo.getProject(), repo.getRoot(), GitCommand.WORKTREE)
    handler.addParameters("add", workingTreePath.path, branchParameterForWorkingTree)
    handler.setSilent(false)
    @Suppress("UsePropertyAccessSyntax")
    handler.apply {
      setStdoutSuppressed(false)
      setStderrSuppressed(false)
    }
    val result = Git.getInstance().runCommand(handler)

    val errorOutput = result.errorOutputAsJoinedString
    assertTrue(errorOutput, result.success())
    assertTrue(errorOutput, errorOutput.contains("Preparing worktree (detached HEAD"))

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
}