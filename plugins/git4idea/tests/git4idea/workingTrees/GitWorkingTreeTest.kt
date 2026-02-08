// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitBranch
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.commands.Git
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.test.git
import git4idea.test.initRepo
import git4idea.test.registerRepo
import git4idea.test.tac
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

internal abstract class GitWorkingTreeTest : GitWorkingTreeTestBase() {

  class GitWorkingTreeOnMainRepoTest : GitWorkingTreeTest() {
    override val mainRepoPath: Path
      get() = repo.root.toNioPath()

    override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
      return listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
    }
  }


  class GitWorkingTreeOnLinkedWorkingTreeTest : GitWorkingTreeTest() {
    val branchName = "feature"
    val mainRepoRelativePath = "mainRepo"

    override val mainRepoPath: Path
      get() = testNioRoot.resolve(mainRepoRelativePath)

    override fun doCreateAndOpenProject(): Project {
      val projectRootPath = getProjectDirOrFile(true)
      initRepo(null, mainRepoPath, true)

      cd(mainRepoPath)
      git(null, "worktree add -B $branchName ../${projectRootPath.fileName}")
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
        GitWorkingTree(mainRepoPath.toString(), "refs/heads/master", true, false),
        GitWorkingTree(repo.root.path, GitBranch.REFS_HEADS_PREFIX + branchName, false, true),
      )
    }
  }

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

    repo.ensureWorkingTreesUpToDateForTests()
    val createdWorkingTrees = repo.workingTreeHolder.getWorkingTrees()
    val workingTree = createdWorkingTrees.firstOrNull { it.path.path.endsWith(treeRoot) }
    assertNotNull(workingTree)

    Git.getInstance().deleteWorkingTree(project, workingTree!!)
    val removedWorkingTree = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)
    assertNull(removedWorkingTree)

    repo.ensureWorkingTreesUpToDateForTests()
    assertSameElements(repo.workingTreeHolder.getWorkingTrees(), getExpectedDefaultWorkingTrees())
  }

  fun listTrees(): List<GitWorkingTree> {
    val listener = GitListWorktreeLineListener(repo)
    val commandResult = Git.getInstance().listWorktrees(repo, listener)
    commandResult.throwOnError()
    return listener.trees
  }

  fun `test creating a worktree with new branch`() {
    doTestWorkingTreeCreation(workingTreeWithNewBranch = true)
  }

  fun `test creating a worktree with existing branch`() {
    doTestWorkingTreeCreation(workingTreeWithNewBranch = false)
  }

  protected fun doTestWorkingTreeCreation(
    workingTreeWithNewBranch: Boolean,
    treeRoot: String = "treeRoot",
    branchName: String = "tree",
    expectedWorkingTree: GitWorkingTree = GitWorkingTree("${testNioRoot.pathString}/$treeRoot",
                                                         GitRefUtil.addRefsHeadsPrefixIfNeeded("tree")!!,
                                                         false, false),
  ) {
    val commit = tac("a.txt")

    val workingTreeDataPath = LocalFilePath(testNioRoot.resolve(treeRoot), true)
    val data = if (workingTreeWithNewBranch) {
      val localBranch = createBranch(repo, "initial-$branchName")
      GitWorkingTreeDialogData.createForNewBranch(workingTreeDataPath, localBranch, branchName)
    }
    else {
      val localBranch = createBranch(repo, branchName)
      GitWorkingTreeDialogData.createForExistingBranch(workingTreeDataPath, localBranch)
    }

    doTestWorkingTreeCreation(data, expectedWorkingTree, branchName, commit)
  }
}