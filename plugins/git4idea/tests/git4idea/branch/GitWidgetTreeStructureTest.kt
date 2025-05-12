// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder
import git4idea.GitUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopup
import git4idea.ui.branch.popup.GitBranchesTreePopupRenderer
import git4idea.ui.branch.popup.GitBranchesTreePopupStepBase
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val TEST_DATA_SUBFOLDER = "widgetTree"

@TestDataPath("\$CONTENT_ROOT/testData/$TEST_DATA_SUBFOLDER")
class GitWidgetTreeStructureTest : GitPlatformTest() {
  private lateinit var repo: GitRepository
  private lateinit var broRepoPath: Path

  private lateinit var popupStep: GitBranchesTreePopupStepBase

  override fun setUp() {
    super.setUp()
    val trinity = setupRepositories(projectPath, "parent", "bro-repo")
    broRepoPath = trinity.bro
    repo = trinity.projectRepo

    cd(projectPath)
    refresh()
    repositoryManager.updateAllRepositories()

    runBlocking {
      // Ensure that the state holder is initialized
      GitRepositoriesFrontendHolder.getInstance(project).init()
    }
  }

  fun testSingleRepo() {
    createRefs(repo)
    repo.checkoutNew("another-branch")

    compareWithSnapshot(buildTestTree())
  }

  fun testSingleRepoWithTags() {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo, ensureTags = true)

    compareWithSnapshot(buildTestTree())
  }

  fun testSingleRepoFiltering() {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo)
    repo.checkoutNew("update")

    compareWithSnapshot(buildTestTree("update"))
  }

  // Favorite refs should be displayed first
  fun testSingleRepoWithFavoriteRefsSorted() {
    GitVcsSettings.getInstance(project).setShowTags(true)
    listOf("a", "b", "c", "Bb", "d", "e-group/a", "f-group/a", "f-group/b").forEach {
      repo.branch(it)
    }
    listOf("a-tag", "b-tag", "c-tag").forEach {
      repo.git("tag $it")
    }

    val branchManager = project.service<GitBranchManager>()
    branchManager.setFavorite(GitBranchType.LOCAL, repo, "d", true)
    branchManager.setFavorite(GitBranchType.LOCAL, repo, "f-group/b ", true)
    branchManager.setFavorite(GitTagType, repo, "c-tag", true)

    repo.update()
    repo.tagHolder.ensureUpToDateForTests()

    compareWithSnapshot(buildTestTree())
  }

  fun testMultiRepo() {
    createRefs(repo)
    repo.checkoutNew("newBranch")

    registerBroRepo().also { broRepo ->
      createRefs(broRepo)
      broRepo.branch("newBranch")
      broRepo.branch("bro-branch")
    }

    compareWithSnapshot(buildTestTree())
  }

  fun testMultiRepoNotFavoriteCurrentBranch() {
    createRefs(repo)
    val currentBranch = "newBranch"
    repo.checkoutNew(currentBranch)
    registerBroRepo().also {
      createRefs(it)
      it.checkoutNew(currentBranch)
    }

    compareWithSnapshot(buildTestTree())
  }


  fun testMultiRepoWithFavoriteRefs() {
    val broRepo = registerBroRepo()

    listOf("a", "b", "c", "d", "e").forEach {
      repo.branch(it)
      broRepo.branch(it)
    }

    // "b" is expected before "master" in the list of common local branch, as it's current
    broRepo.checkout("b")

    val branchManager = project.service<GitBranchManager>()
    // if a common branch is favorite in all repos, it should be displayed first
    branchManager.setFavorite(GitBranchType.LOCAL, repo, "e", true)
    branchManager.setFavorite(GitBranchType.LOCAL, broRepo, "e", true)
    // but not if it's favorite in a single repo
    branchManager.setFavorite(GitBranchType.LOCAL, repo, "c", true)

    compareWithSnapshot(buildTestTree())
  }

  fun testMultiRepoWithoutSync() {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC
    createRefs(repo)
    repo.checkoutNew("newBranch")

    registerBroRepo().also {
      createRefs(it)
    }

    compareWithSnapshot(buildTestTree())
  }

  fun testMultiRepoWithoutSyncWithFilter() {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC
    createRefs(repo)
    registerBroRepo().also {
      createRefs(it)
    }

    compareWithSnapshot(buildTestTree("group"))
  }


  fun testMultiRepoWithFilterMatchingRepo() {
    registerBroRepo()
    repo.branch("project-branch")

    compareWithSnapshot(buildTestTree("ro"))
  }

  fun testMultiRepoWithFilter() {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo, ensureTags = true)
    registerBroRepo().also {
      createRefs(it, ensureTags = true)
    }

    compareWithSnapshot(buildTestTree("group"))
  }

  fun testSingleFreshRepo() {
    resetToFreshState(repo)
    compareWithSnapshot(buildTestTree())
  }

  fun testMultipleFreshRepos() {
    resetToFreshState(repo)
    registerBroRepo().also { resetToFreshState(it) }
    compareWithSnapshot(buildTestTree())
  }

  fun testMultipleFreshReposNoSync() {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC

    resetToFreshState(repo)
    registerBroRepo().also { resetToFreshState(it) }
    compareWithSnapshot(buildTestTree())
  }

  private fun resetToFreshState(repo: GitRepository) {
    repo.root.toNioPath().resolve(GitUtil.DOT_GIT).deleteRecursively()
    repo.git("init")
  }

  private fun createRefs(repo: GitRepository, ensureTags: Boolean = false) {
    listOf("test", "group/test", "another/group/test").forEach {
      repo.branch(it)
      repo.git("push -u origin $it")
    }
    listOf("v1", "group/v2").forEach {
      repo.git("tag $it")
    }
    if (ensureTags) {
      repo.tagHolder.ensureUpToDateForTests()
    }
  }

  private fun registerBroRepo(): GitRepository = registerRepo(project, broRepoPath)

  private fun compareWithSnapshot(tree: Tree, snapshotName: String? = null) {
    val testDataFileName = snapshotName ?: PlatformTestUtil.getTestName(name, false)
    val testData = TestDataUtil.basePath.resolve(TEST_DATA_SUBFOLDER).resolve(testDataFileName)

    val printedTree = invokeAndWaitIfNeeded {
      TreeTestUtil(tree)
        .setSelection(true)
        .setConverter { node: Any ->
          val icon = (tree.cellRenderer as GitBranchesTreeRenderer).getIcon(node, false)
          val textByRenderer = popupStep.getNodeText(node)
          val text = when {
            textByRenderer != null -> textByRenderer
            node is SeparatorWithText -> "-----"
            else -> PlatformTestUtil.toString(node, null)
          }
          "$text${if (icon == null) "" else " [$icon]"}"
        }
        // Skip root
        .setFilter { it.pathCount != 1 }
        .toString()
        .trimIndent()
    }

    Assertions.assertThat(printedTree).toMatchSnapshot(testData)
  }

  private fun buildTestTree(filter: String? = null): Tree {
    repositoryManager.updateAllRepositories()

    return invokeAndWaitIfNeeded {
      //TODO replace with the actual tree from GitBranchesTreePopupBase
      val tree = Tree()
      popupStep = GitBranchesTreePopup.createBranchesTreePopupStep(project, repo)
      tree.cellRenderer = GitBranchesTreePopupRenderer(popupStep)
      tree.model = popupStep.treeModel
      popupStep.updateTreeModelIfNeeded(tree, filter)
      popupStep.setSearchPattern(filter)

      tree.also {
        TreeTestUtil(it).expandAll()
      }
    }
  }
}
