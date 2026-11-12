// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.git.branch.popup.GitBranchesPopupStepBase
import com.intellij.vcs.git.branch.popup.GitDefaultBranchesPopupStep
import com.intellij.vcs.git.branch.popup.GitDefaultBranchesTreeRenderer
import com.intellij.vcs.git.branch.tree.GitBranchesTreeRenderer
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.GitUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.GitPlatformTestContext
import git4idea.test.TestDataUtil
import git4idea.test.gitPlatformContextFixture
import git4idea.test.branch
import git4idea.test.cd
import git4idea.test.checkout
import git4idea.test.checkoutNew
import git4idea.test.git
import git4idea.test.gitInit
import git4idea.test.registerRepo
import git4idea.test.setupRepositories
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

private const val TEST_DATA_SUBFOLDER = "widgetTree"

@TestApplication
@TestDataPath($$"$CONTENT_ROOT/../testData/$$TEST_DATA_SUBFOLDER")
class GitWidgetTreeStructureTest {
  private lateinit var repo: GitRepository
  private lateinit var broRepoPath: Path

  private lateinit var popupStep: GitBranchesPopupStepBase

  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()
  private lateinit var testMethodName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo): Unit = with(context) {
    testMethodName = testInfo.testMethod.orElseThrow().name
    val trinity = setupRepositories(project, testNioRoot, projectPath, "parent", "bro-repo")
    broRepoPath = trinity.bro
    repo = trinity.projectRepo

    cd(projectPath)
    refresh()
    repositoryManager.updateAllRepositories()

    runBlocking {
      // Ensure that the state holder is initialized
      GitRepositoriesHolder.getInstance(project).awaitInitialization()
    }
  }

  @Test
  fun testSingleRepo() {
    createRefs(repo)
    repo.checkoutNew("another-branch")

    compareWithSnapshot(buildTestTree())
  }

  @Test
  fun testSingleRepoWithTags(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo, ensureTags = true)

    compareWithSnapshot(buildTestTree())
  }

  @Test
  fun testSingleRepoFiltering(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo)
    repo.checkoutNew("update")

    compareWithSnapshot(buildTestTree("update"))
  }

  // Favorite refs should be displayed first
  @Test
  fun testSingleRepoWithFavoriteRefsSorted(): Unit = with(context) {
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
    (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()

    compareWithSnapshot(buildTestTree())
  }

  @Test
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

  @Test
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


  @Test
  fun testMultiRepoWithFavoriteRefs(): Unit = with(context) {
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

  @Test
  fun testMultiRepoWithoutSync(): Unit = with(context) {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC
    createRefs(repo)
    repo.checkoutNew("newBranch")

    registerBroRepo().also {
      createRefs(it)
    }

    compareWithSnapshot(buildTestTree())
  }

  @Test
  fun testMultiRepoWithoutSyncWithFilter(): Unit = with(context) {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC
    createRefs(repo)
    registerBroRepo().also {
      createRefs(it)
    }

    compareWithSnapshot(buildTestTree("group"))
  }


  @Test
  fun testMultiRepoWithFilterMatchingRepo() {
    registerBroRepo()
    repo.branch("project-branch")

    compareWithSnapshot(buildTestTree("ro"))
  }

  @Test
  fun testMultiRepoWithFilter(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setShowTags(true)
    createRefs(repo, ensureTags = true)
    registerBroRepo().also {
      createRefs(it, ensureTags = true)
    }

    compareWithSnapshot(buildTestTree("group"))
  }

  @Test
  fun testSingleFreshRepo() {
    resetToFreshState(repo)
    compareWithSnapshot(buildTestTree())
  }

  @Test
  fun testMultipleFreshRepos() {
    resetToFreshState(repo)
    registerBroRepo().also { resetToFreshState(it) }
    compareWithSnapshot(buildTestTree())
  }

  @Test
  fun testMultipleFreshReposNoSync(): Unit = with(context) {
    settings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC

    resetToFreshState(repo)
    registerBroRepo().also { resetToFreshState(it) }
    compareWithSnapshot(buildTestTree())
  }

  private fun resetToFreshState(repo: GitRepository): Unit = with(context) {
    repo.root.toNioPath().resolve(GitUtil.DOT_GIT).deleteRecursively()
    cd(repo)
    gitInit(project)
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
      (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()
    }
  }

  private fun registerBroRepo(): GitRepository = with(context) {
    registerRepo(project, broRepoPath)
  }

  private fun compareWithSnapshot(tree: Tree, snapshotName: String? = null) {
    val testDataFileName = snapshotName ?: PlatformTestUtil.getTestName(testMethodName, false)
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

  private fun buildTestTree(filter: String? = null): Tree = with(context) {
    repositoryManager.updateAllRepositories()

    return invokeAndWaitIfNeeded {
      val holder = GitRepositoriesHolder.getInstance(project)
      val repositories = holder.getAll()
      //TODO replace with the actual tree from GitBranchesTreePopupBase
      val tree = Tree()
      val preferredSelection = checkNotNull(holder.get(repo.repositoryId()))
      popupStep = GitDefaultBranchesPopupStep.create(project, preferredSelection, repositories)
      tree.cellRenderer = GitDefaultBranchesTreeRenderer(popupStep)
      tree.model = popupStep.treeModel
      popupStep.updateTreeModelIfNeeded(tree, filter)
      popupStep.setSearchPattern(filter)

      tree.also {
        TreeTestUtil(it).expandAll()
      }
    }
  }
}
