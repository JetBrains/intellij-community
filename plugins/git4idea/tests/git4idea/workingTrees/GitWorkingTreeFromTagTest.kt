// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import git4idea.GitTag
import git4idea.GitWorkingTree
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.tac
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitWorkingTreeFromTagTest : GitWorkingTreeTestBase() {
  private val fixture: TestFixture<GitPlatformTestContext> = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()
  private lateinit var repo: GitRepository

  private fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
  }

  @BeforeEach
  fun setUp() {
    repo = createRepository(context.project, context.projectNioRoot, true)
  }

  @Test
  fun `test creating a worktree from tag`(): Unit = with(context) {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag $tagName")

    val treeRoot = "treeRoot"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorktreeCreationRequest(repo, workingTreeDataPath, WorktreeBranchSpec.CheckoutExisting(GitTag(tagName)))

    repo.doTestWorkingTreeCreation(
      data,
      projectNioRoot,
      GitWorkingTree(workingTreeDataPath.path, null, false, false, headHash = commit),
      expectedWorkingTreeBranchName = null,
      expectedWorkingTreeLastCommit = commit,
      getExpectedDefaultWorkingTrees()
    )
  }

  @Test
  fun `test creating a worktree from annotated tag`(): Unit = with(context) {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag -a $tagName -m annotated")

    val treeRoot = "treeRoot"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorktreeCreationRequest(repo, workingTreeDataPath, WorktreeBranchSpec.CheckoutExisting(GitTag(tagName)))

    repo.doTestWorkingTreeCreation(
      data,
      projectNioRoot,
      GitWorkingTree(workingTreeDataPath.path, null, false, false, headHash = commit),
      expectedWorkingTreeBranchName = null,
      expectedWorkingTreeLastCommit = commit,
      getExpectedDefaultWorkingTrees()
    )
  }

  @Test
  fun `test creating a worktree from tag with new branch`(): Unit = with(context) {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag $tagName")

    val treeRoot = "treeRoot"
    val newBranchName = "branch-from-tag"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorktreeCreationRequest(repo, workingTreeDataPath, WorktreeBranchSpec.CreateNewBranch(GitTag(tagName), newBranchName))

    repo.doTestWorkingTreeCreation(
      data,
      projectNioRoot,
      GitWorkingTree(workingTreeDataPath.path,
                     GitRefUtil.addRefsHeadsPrefixIfNeeded(newBranchName)!!,
                     false, false),
      expectedWorkingTreeBranchName = newBranchName,
      expectedWorkingTreeLastCommit = commit,
      getExpectedDefaultWorkingTrees()
    )
  }
}
