// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import git4idea.workingTrees.ui.GitRepositoryHeader
import git4idea.workingTrees.ui.GitWorktreeRow
import git4idea.workingTrees.ui.GitWorktreesTabModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Multi-root worktree behavior for the *peer* case (a container of sibling repositories, container itself
 * not a git repo). Reads worktrees through the shared repository model, as the tab does.
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitMultiRepoWorkingTreeTest {
  private val contextFixture = gitWorkingTreePlatformFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repoA: GitRepository
  private lateinit var repoB: GitRepository
  private lateinit var holder: GitRepositoriesHolder

  private fun GitPlatformTestContext.setUpRepos() {
    repoA = createRepository(project, projectNioRoot, true)
    repoB = createRepository(project, testNioRoot.resolve("community"), true)
    holder = GitRepositoriesHolder.getAndInit(project)
    syncWorktrees(repoA)
    syncWorktrees(repoB)
  }

  private val tabModel: GitWorktreesTabModel get() = GitWorktreesTabModel(context.project)

  @Test
  fun `test sibling repositories form a multi-repository project`(): Unit = with(context) {
    setUpRepos()
    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    assertThat(status).isInstanceOf(GitWorktreeSupportStatus.MultipleRepository::class.java)
    assertThat((status as GitWorktreeSupportStatus.MultipleRepository).repositories).containsExactlyInAnyOrder(repoA, repoB)
  }

  @Test
  fun `test entries are grouped by repository with a header per repository`(): Unit = with(context) {
    setUpRepos()
    val entries = runBlocking { tabModel.buildEntries() }

    val headerRoots = entries.filterIsInstance<GitRepositoryHeader>().map { it.repository.root.path }
    assertThat(headerRoots).containsExactlyInAnyOrder(repoA.root.path, repoB.root.path)

    val rows = entries.filterIsInstance<GitWorktreeRow>()
    assertThat(rows.map { it.repository.root.path }).containsExactlyInAnyOrder(repoA.root.path, repoB.root.path)
    assertThat(rows.all { it.gitWorkingTree.isMain }).describedAs("Each repository contributes its main worktree row").isTrue()

    // a header always precedes the rows of its repository
    entries.forEachIndexed { index, entry ->
      if (entry is GitWorktreeRow) {
        val precedingHeader = entries.subList(0, index).filterIsInstance<GitRepositoryHeader>().lastOrNull()
        assertThat(precedingHeader?.repository?.root?.path).isEqualTo(entry.repository.root.path)
      }
    }
  }

  @Test
  fun `test tab is not auto-shown when every repository has a single worktree`(): Unit = with(context) {
    setUpRepos()
    assertThat(GitWorkingTreesService.getInstance(project).shouldWorkingTreesTabBeShown()).isFalse()
  }

  @Test
  fun `test tab is auto-shown when any repository has more than one worktree`(): Unit = with(context) {
    setUpRepos()
    addWorktree(repoB, "feature", "../community-feature")
    syncWorktrees(repoB)

    assertThat(GitWorkingTreesService.getInstance(project).shouldWorkingTreesTabBeShown()).isTrue()
  }

  @Test
  fun `test creating a worktree for one repository does not affect the others`(): Unit = with(context) {
    setUpRepos()
    addWorktree(repoB, "feature", "../community-feature")
    repoA.reloadBackend()
    repoB.reloadBackend()

    assertThat(repoA.workingTreeHolder.getWorkingTrees()).describedAs("The untouched repository must keep only its main worktree")
      .hasSize(1)
    assertThat(repoB.workingTreeHolder.getWorkingTrees()).describedAs("The repository with the new worktree must have two worktrees")
      .hasSize(2)
  }

  @Test
  fun `test selected repository is resolved from the selection`(): Unit = with(context) {
    setUpRepos()
    val entries = runBlocking { tabModel.buildEntries() }
    val headerA = entries.filterIsInstance<GitRepositoryHeader>().single { it.repository.root.path == repoA.root.path }
    val rowB = entries.filterIsInstance<GitWorktreeRow>().first { it.repository.root.path == repoB.root.path }

    assertThat(tabModel.resolveSelectedRepositoryModel(listOf(headerA))?.root?.path).isEqualTo(repoA.root.path)
    assertThat(tabModel.resolveSelectedRepositoryModel(listOf(rowB))?.root?.path).isEqualTo(repoB.root.path)
    assertThat(tabModel.resolveSelectedRepositoryModel(emptyList()))
      .describedAs("Empty selection in a multi-repo project must not resolve a repository").isNull()
    assertThat(tabModel.resolveSelectedRepositoryModel(listOf(headerA, rowB)))
      .describedAs("A selection spanning several repositories must not resolve a single repository").isNull()
  }

  @Test
  fun `test detached worktree on a tagged commit shows the tag name`(): Unit = with(context) {
    setUpRepos()
    cd(repoB.root.path)
    git("tag v1.0")
    git("worktree add --detach ../community-v1 v1.0")
    refresh()
    (repoB.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()
    syncWorktrees(repoB)

    val detachedRow = runBlocking { tabModel.buildEntries() }.filterIsInstance<GitWorktreeRow>()
      .first { it.repository.root.path == repoB.root.path && !it.gitWorkingTree.isMain }
    assertThat(detachedRow.presentableBranchName).isEqualTo("v1.0")
  }

  @Test
  fun `test a branch checked out in another repository's worktree is detected across repositories`(): Unit = with(context) {
    setUpRepos()
    addWorktree(repoB, "feature", "../community-feature")
    repoB.reloadBackend()

    val featureWorktree = repoB.workingTreeHolder.getWorkingTrees().single { !it.isMain }
    val featureBranch = featureWorktree.currentBranch!!

    assertThat(GitSingleRefAction.isCurrentRefInAnyOtherWorkingTree(featureBranch, listOf(repoA, repoB)))
      .describedAs("Searching across all repositories must find the worktree that holds the branch").isTrue()
    assertThat(GitSingleRefAction.isCurrentRefInAnyOtherWorkingTree(featureBranch, listOf(repoA)))
      .describedAs("Searching only an unrelated repository must not find it").isFalse()
  }

  @Test
  fun `test creating a worktree for one repository in a multi-root project affects only that repository`(): Unit = with(context) {
    setUpRepos()
    val worktreePath = LocalFilePath(testNioRoot.resolve("repoB-worktree"), true)
    val request = GitWorktreeCreationRequest(repoB, worktreePath,
                                             WorktreeBranchSpec.CreateNewBranch(repoB.currentBranch!!, "feature-b"))

    holder.expectEvent(
      {
        val result = GitWorkingTreesService.getInstance(project).createWorkingTree(request)
        assertThat(result.success).describedAs(result.errorOutputAsHtmlString).isTrue()
        val worktreesDir = LocalFileSystem.getInstance()
          .refreshAndFindFileByNioFile(repoB.root.toNioPath().resolve(".git/worktrees"))
        refresh(worktreesDir!!)
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )

    repoA.reloadBackend()
    repoB.reloadBackend()

    assertThat(repoA.workingTreeHolder.getWorkingTrees()).describedAs("The untouched repository keeps a single worktree").hasSize(1)
    assertThat(repoB.workingTreeHolder.getWorkingTrees()).describedAs("The target repository gains the new worktree").hasSize(2)
  }

  @Test
  fun `test creating a worktree into an occupied directory fails without throwing`(): Unit = with(context) {
    setUpRepos()
    val occupied = testNioRoot.resolve("occupied")
    occupied.createDirectories()
    occupied.resolve("file.txt").writeText("content")

    val request = GitWorktreeCreationRequest(repoB, LocalFilePath(occupied, true),
                                             WorktreeBranchSpec.CreateNewBranch(repoB.currentBranch!!, "feature-c"))

    val result = runBlocking { GitWorkingTreesService.getInstance(project).createWorkingTree(request) }
    assertThat(result.success).describedAs("Creating into a non-empty directory must fail gracefully").isFalse()
  }

  @Test
  fun `test resolveOwningProjectPath picks the deepest owning candidate`(): Unit = with(context) {
    val app = testNioRoot.resolve("app")
    val worktree = app.resolve("worktrees").resolve("feature")
    val candidates = listOf(testNioRoot, app, testNioRoot.resolve("other"))

    assertThat(GitWorkingTreesService.resolveOwningProjectPath(worktree, candidates)).isEqualTo(app)
  }

  @Test
  fun `test resolveOwningProjectPath falls back to the worktree path when no candidate owns it`(): Unit = with(context) {
    val worktree = testNioRoot.resolve("app").resolve("worktrees").resolve("feature")
    val candidates = listOf(testNioRoot.resolve("other"), Path("/tmp/unrelated"))

    assertThat(GitWorkingTreesService.resolveOwningProjectPath(worktree, candidates)).isEqualTo(worktree)
  }

  @Test
  fun `test opening a linked worktree opens the worktree itself, not a parent project it lives in`(): Unit = with(context) {
    val projectBase = testNioRoot.resolve("proj")
    val worktreePath = projectBase.resolve("sub-feature")
    val linked = GitWorkingTree(worktreePath.toString(), "feature", isMain = false, isCurrent = true)
    // The worktree lives inside the currently open project; it must still open standalone, not reopen the parent.
    val candidates = listOf(projectBase, projectBase.resolve("sub"))

    assertThat(GitWorkingTreesService.resolveProjectPathToOpen(linked, candidates)).isEqualTo(worktreePath)
  }

  @Test
  fun `test opening the main worktree resolves its owning project`(): Unit = with(context) {
    val projectBase = testNioRoot.resolve("proj")
    val mainWorktreePath = projectBase.resolve("rootA")
    val main = GitWorkingTree(mainWorktreePath.toString(), "master", isMain = true, isCurrent = true)
    val candidates = listOf(projectBase)

    assertThat(GitWorkingTreesService.resolveProjectPathToOpen(main, candidates)).isEqualTo(projectBase)
  }

  @Test
  fun `test opening the main worktree falls back to the worktree when no owner is known`(): Unit = with(context) {
    val mainWorktreePath = testNioRoot.resolve("standalone")
    val main = GitWorkingTree(mainWorktreePath.toString(), "master", isMain = true, isCurrent = true)
    val candidates = listOf(testNioRoot.resolve("other"))

    assertThat(GitWorkingTreesService.resolveProjectPathToOpen(main, candidates)).isEqualTo(mainWorktreePath)
  }

  private fun GitPlatformTestContext.addWorktree(repository: GitRepository, branch: String, relativePath: String) {
    cd(repository.root.path)
    git("worktree add -B $branch $relativePath")
    refresh()
  }

  private fun syncWorktrees(repository: GitRepository) {
    holder.expectEvent(
      { withContext(Dispatchers.IO) { (repository.workingTreeHolder as GitWorkingTreeHolderImpl).updateState() } },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )
  }

  private fun GitRepository.reloadBackend() {
    runBlocking {
      withContext(Dispatchers.IO) {
        (workingTreeHolder as GitWorkingTreeHolderImpl).updateState()
      }
    }
  }
}
