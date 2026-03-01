// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.LocalFilePath
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.test.cloneRepo
import git4idea.test.git
import git4idea.test.initRepo
import git4idea.test.registerRepo
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

internal class GitWorkingTreeFromRemoteBranchesTest : GitWorkingTreeTestBase() {

  val remoteBranchName = "remoteBranch"
  val remoteRepoRelativePath = "remoteRepo"
  lateinit var lastCommitInRemoteBranch: String

  override val mainRepoPath: Path
    get() = projectNioRoot

  override fun doCreateAndOpenProject(): Project {
    val remoteRepoPath = testNioRoot.resolve(remoteRepoRelativePath)
    initRepo(null, remoteRepoPath, true)
    val file = "a.txt"
    touch(file, "content" + Math.random())
    git(null, "add $file")
    git(null, "commit -m initial")
    lastCommitInRemoteBranch = git(null, "log -1 --pretty=%H")
    git(null, "branch $remoteBranchName")

    val projectRootPath = getProjectDirOrFile(true)
    cloneRepo(remoteRepoPath.toString(), projectRootPath.toString(), false)

    Files.createDirectories(projectRootPath.resolve(Project.DIRECTORY_STORE_FOLDER))
    return runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(projectIdentityFile = projectRootPath, options = OpenProjectTask {})!!
    }
  }

  override fun createRepository(): GitRepository {
    return registerRepo(project, projectNioRoot)
  }

  override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(
      GitWorkingTree(repo.toString(), "refs/heads/master", true, true)
    )
  }

  fun `test creating a worktree from remote branch`() {
    doTestWorkingTreeFromRemoteBranchCreation(false)
  }

  fun `test creating a worktree from remote branch with custom name`() {
    doTestWorkingTreeFromRemoteBranchCreation(true)
  }

  private fun doTestWorkingTreeFromRemoteBranchCreation(withNewBranch: Boolean) {
    val remoteBranch = repo.branches.findRemoteBranch("origin/$remoteBranchName")!!
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve("treeRoot"), true)
    val data = if (withNewBranch) {
      GitWorkingTreeDialogData.createForNewBranch(workingTreeDataPath, remoteBranch, remoteBranchName)
    }
    else {
      GitWorkingTreeDialogData.createForExistingBranch(workingTreeDataPath, remoteBranch)
    }

    doTestWorkingTreeCreation(data,
                              GitWorkingTree(data.workingTreePath.path,
                                             GitRefUtil.addRefsHeadsPrefixIfNeeded(remoteBranchName)!!,
                                             false, false),
                              remoteBranchName,
                              lastCommitInRemoteBranch)
  }
}