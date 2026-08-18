// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cloneRepo
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.initRepo
import git4idea.test.registerRepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

private const val REMOTE_BRANCH_NAME = "remoteBranch"
private const val REMOTE_REPO_RELATIVE_PATH = "remoteRepo"
private const val PROJECT_DIR_NAME = "project"

@TestApplication
internal class GitWorkingTreeFromRemoteBranchesTest : GitWorkingTreeTestBase() {
  private val fixture: TestFixture<GitPlatformTestContext> = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()
  private lateinit var repo: GitRepository

  /**
   * Creates a repository with a [REMOTE_BRANCH_NAME] branch in `<testRoot>/remoteRepo` and clones it into
   * `<testRoot>/project`, which then becomes the project directory.
   */
  @BeforeEach
  fun setUp() {
    with(context) {
      val projectPath = testNioRoot.resolve(PROJECT_DIR_NAME)
      val remoteRepoPath = testNioRoot.resolve(REMOTE_REPO_RELATIVE_PATH)

      initRepo(project = null, remoteRepoPath, makeInitialCommit = true)
      val file = "a.txt"
      Executor.touch(file, "content" + Math.random())
      git(null, "add $file")
      git(null, "commit -m initial")
      git(null, "branch $REMOTE_BRANCH_NAME")

      cloneRepo(project = null, remoteRepoPath.toString(), projectPath.toString(), bare = false)
      // makes `projectFixture` open the prepared directory instead of creating a new project
      Files.createDirectories(projectPath.resolve(Project.DIRECTORY_STORE_FOLDER))

      repo = registerRepo(project, projectNioRoot)
    }
  }

  private fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(
      GitWorkingTree(repo.toString(), "refs/heads/master", true, true)
    )
  }

  @Test
  fun `test creating a worktree from remote branch`() {
    doTestWorkingTreeFromRemoteBranchCreation(withNewBranch = false)
  }

  @Test
  fun `test creating a worktree from remote branch with custom name`() {
    doTestWorkingTreeFromRemoteBranchCreation(withNewBranch = true)
  }

  private fun doTestWorkingTreeFromRemoteBranchCreation(withNewBranch: Boolean): Unit = with(context) {
    val remoteBranch = repo.branches.findRemoteBranch("origin/$REMOTE_BRANCH_NAME")!!
    val lastCommitInRemoteBranch = git("log -1 --pretty=%H origin/$REMOTE_BRANCH_NAME")

    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve("treeRoot"), true)
    val data = if (withNewBranch) {
      GitWorkingTreeDialogData.createForNewBranch(workingTreeDataPath, remoteBranch, REMOTE_BRANCH_NAME)
    }
    else {
      GitWorkingTreeDialogData.createForExistingBranch(workingTreeDataPath, remoteBranch)
    }

    repo.doTestWorkingTreeCreation(
      data,
      projectNioRoot,
      GitWorkingTree(data.workingTreePath.path,
                     GitRefUtil.addRefsHeadsPrefixIfNeeded(REMOTE_BRANCH_NAME)!!,
                     false, false),
      REMOTE_BRANCH_NAME,
      lastCommitInRemoteBranch, getExpectedDefaultWorkingTrees())
  }
}

