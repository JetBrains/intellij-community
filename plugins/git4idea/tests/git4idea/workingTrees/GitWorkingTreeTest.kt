// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitWorkingTree
import git4idea.workingTrees.dialog.GitWorkingTreeDialogData
import git4idea.commands.Git
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.branch
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.initRepo
import git4idea.test.registerRepo
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

private const val LINKED_WORKING_TREE_BRANCH_NAME = "feature"
private const val MAIN_REPO_RELATIVE_PATH = "mainRepo"
private const val PROJECT_DIR_NAME = "project"

@TestApplication
internal class GitWorkingTreeOnMainRepoTest : GitWorkingTreeTest() {

  @BeforeEach
  fun setUp() {
    with(context) {
      mainRepoPath = projectNioRoot
      repo = createRepository(project, projectNioRoot, true)
    }
  }

  override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
  }
}

@TestApplication
internal class GitWorkingTreeOnLinkedWorkingTreeTest : GitWorkingTreeTest() {

  override fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree> {
    return listOf(
      GitWorkingTree(mainRepoPath.toString(), "refs/heads/master", true, false),
      GitWorkingTree(repo.root.path, GitBranch.REFS_HEADS_PREFIX + LINKED_WORKING_TREE_BRANCH_NAME, false, true),
    )
  }

  /**
   * Creates the main repository in `<testRoot>/mainRepo` and adds a linked working tree in `<testRoot>/project`,
   * which then becomes the project directory.
   */
  @BeforeEach
  fun setUp() {
    with(context) {
      mainRepoPath = testNioRoot.resolve(MAIN_REPO_RELATIVE_PATH)

      initRepo(project = null, mainRepoPath, makeInitialCommit = true)

      cd(mainRepoPath)
      git(null, "worktree add -B $LINKED_WORKING_TREE_BRANCH_NAME ../$PROJECT_DIR_NAME")
      val projectPath = testNioRoot.resolve(PROJECT_DIR_NAME) // created by `git worktree add`
      // makes `projectFixture` open the prepared directory instead of creating a new project
      Files.createDirectories(projectPath.resolve(Project.DIRECTORY_STORE_FOLDER))

      repo = registerRepo(project, projectNioRoot)
    }
  }
}

internal abstract class GitWorkingTreeTest : GitWorkingTreeTestBase() {

  private val fixture: TestFixture<GitPlatformTestContext> = gitPlatformContextFixture()
  protected val context: GitPlatformTestContext get() = fixture.get()
  protected lateinit var repo: GitRepository
  protected lateinit var mainRepoPath: Path

  abstract fun getExpectedDefaultWorkingTrees(): List<GitWorkingTree>

  @Test
  fun `test listing working trees`() {
    val trees = listTrees()
    assertThat(trees).containsExactlyInAnyOrderElementsOf(getExpectedDefaultWorkingTrees())
  }

  @Test
  fun `test deleting working tree`(): Unit = with(context) {
    val branch = "tree"
    val treeRoot = "treeRoot"
    val newWorkingTreeRootPath = testNioRoot.resolve(treeRoot)

    git("worktree add -B $branch ../$treeRoot")

    val createdWorkTreeRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)
    assertThat(createdWorkTreeRoot).isNotNull()

    repo.ensureWorkingTreesUpToDateForTests()
    val createdWorkingTrees = repo.workingTreeHolder.getWorkingTrees()
    val workingTree = createdWorkingTrees.firstOrNull { it.path.path.endsWith(treeRoot) }
    assertThat(workingTree).isNotNull()

    Git.getInstance().deleteWorkingTree(repo, workingTree!!)
    assertThat(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)).isNull()

    repo.ensureWorkingTreesUpToDateForTests()
    assertThat(repo.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(getExpectedDefaultWorkingTrees())
  }

  @Test
  fun `test deleting working tree after manual deletion of the folder`(): Unit = with(context) {
    val branch = "tree"
    val treeRoot = "treeRoot"
    val newWorkingTreeRootPath = testNioRoot.resolve(treeRoot)

    git("worktree add -B $branch ../$treeRoot")

    val createdWorkTreeRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)
    assertThat(createdWorkTreeRoot).isNotNull()

    repo.ensureWorkingTreesUpToDateForTests()
    val createdWorkingTrees = repo.workingTreeHolder.getWorkingTrees()
    val workingTree = createdWorkingTrees.firstOrNull { it.path.path.endsWith(treeRoot) }
    assertThat(workingTree).isNotNull()

    NioFiles.deleteRecursively(newWorkingTreeRootPath)
    assertThat(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)).isNull()
    repo.ensureWorkingTreesUpToDateForTests()

    Git.getInstance().deleteWorkingTree(repo, workingTree!!)
    assertThat(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)).isNull()

    repo.ensureWorkingTreesUpToDateForTests()
    assertThat(repo.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(getExpectedDefaultWorkingTrees())
  }

  fun listTrees(): List<GitWorkingTree> {
    return Git.getInstance().listWorktrees(repo)
  }

  @Test
  fun `test creating a worktree with new branch`() {
    doTestWorkingTreeCreation(workingTreeWithNewBranch = true)
  }

  @Test
  fun `test creating a worktree with existing branch`() {
    doTestWorkingTreeCreation(workingTreeWithNewBranch = false)
  }

  private fun doTestWorkingTreeCreation(
    workingTreeWithNewBranch: Boolean,
    treeRoot: String = "treeRoot",
    branchName: String = "tree",
  ): Unit = with(context) {
    val expectedWorkingTree = GitWorkingTree("${testNioRoot.pathString}/$treeRoot",
                                             GitRefUtil.addRefsHeadsPrefixIfNeeded("tree")!!,
                                             false, false)
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

    repo.doTestWorkingTreeCreation(
      data,
      mainRepoPath,
      expectedWorkingTree,
      branchName,
      commit,
      getExpectedDefaultWorkingTrees()
    )
  }

  private fun createBranch(repo: GitRepository, branchName: String): GitLocalBranch {
    repo.branch(branchName)
    repo.update()
    val newBranch = repo.branches.findLocalBranch(branchName)
    assertThat(newBranch).describedAs("Branch $branchName was not created").isNotNull()
    return newBranch!!
  }
}