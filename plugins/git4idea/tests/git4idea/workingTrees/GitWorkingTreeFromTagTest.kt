// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vcs.LocalFilePath
import git4idea.GitTag
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.repo.GitRefUtil
import git4idea.test.tac
import java.nio.file.Path

internal class GitWorkingTreeFromTagTest : GitWorkingTreeTestBase() {

  override val mainRepoPath: Path
    get() = repo.root.toNioPath()

  override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
  }

  fun `test creating a worktree from tag`() {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag $tagName")

    val treeRoot = "treeRoot"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorkingTreeDialogData.createForExistingBranch(workingTreeDataPath, GitTag(tagName))

    doTestWorkingTreeCreation(
      data,
      GitWorkingTree(workingTreeDataPath.path, null, false, false, headHash = commit),
      expectedWorkingTreeBranchName = null,
      expectedWorkingTreeLastCommit = commit,
    )
  }

  fun `test creating a worktree from annotated tag`() {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag -a $tagName -m annotated")

    val treeRoot = "treeRoot"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorkingTreeDialogData.createForExistingBranch(workingTreeDataPath, GitTag(tagName))

    doTestWorkingTreeCreation(
      data,
      GitWorkingTree(workingTreeDataPath.path, null, false, false, headHash = commit),
      expectedWorkingTreeBranchName = null,
      expectedWorkingTreeLastCommit = commit,
    )
  }

  fun `test creating a worktree from tag with new branch`() {
    val commit = tac("a.txt")
    val tagName = "v1.0"
    git("tag $tagName")

    val treeRoot = "treeRoot"
    val newBranchName = "branch-from-tag"
    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = GitWorkingTreeDialogData.createForNewBranch(workingTreeDataPath, GitTag(tagName), newBranchName)

    doTestWorkingTreeCreation(
      data,
      GitWorkingTree(workingTreeDataPath.path,
                     GitRefUtil.addRefsHeadsPrefixIfNeeded(newBranchName)!!,
                     false, false),
      expectedWorkingTreeBranchName = newBranchName,
      expectedWorkingTreeLastCommit = commit,
    )
  }
}
