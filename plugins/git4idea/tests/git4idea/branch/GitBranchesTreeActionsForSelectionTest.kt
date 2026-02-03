// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.containers.stream
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.git.actions.branch.GitCheckoutWithUpdateAction
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.actions.branch.GitCheckoutAsNewBranch
import git4idea.actions.branch.GitCheckoutWithRebaseAction
import git4idea.actions.branch.GitCompareWithBranchAction
import git4idea.actions.branch.GitPullBranchAction
import git4idea.actions.branch.GitPushBranchAction
import git4idea.actions.branch.GitRebaseBranchAction
import git4idea.actions.branch.GitRenameBranchAction
import git4idea.actions.branch.GitTrackedBranchActionGroup
import git4idea.actions.branch.GitUpdateSelectedBranchAction
import git4idea.actions.ref.GitCheckoutAction
import git4idea.actions.ref.GitDeleteRefAction
import git4idea.actions.ref.GitMergeRefAction
import git4idea.actions.ref.GitShowDiffWithRefAction
import git4idea.actions.tag.GitPushTagActionWrapper
import git4idea.branch.ActionState.Companion.isDisabledAndVisible
import git4idea.branch.ActionState.Companion.isEnabledAndVisible
import git4idea.branch.GitBranchesTreeTestContext.Companion.NOT_ORIGIN
import git4idea.branch.GitBranchesTreeTestContext.Companion.ORIGIN
import git4idea.branch.GitBranchesTreeTestContext.Companion.branchInfo
import git4idea.branch.GitBranchesTreeTestContext.Companion.tagInfo
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRepositoryTagsHolder
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.MockGitRepository
import git4idea.test.MockGitRepositoryModel
import git4idea.ui.branch.dashboard.BRANCHES_UI_CONTROLLER
import git4idea.ui.branch.dashboard.BranchNodeDescriptor
import git4idea.ui.branch.dashboard.BranchesDashboardActions
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchActionsBuilder
import git4idea.ui.branch.dashboard.BranchesDashboardTreeController
import git4idea.ui.branch.dashboard.BranchesTreeSelection
import git4idea.ui.branch.dashboard.BranchesTreeSelection.Companion.getSelectedRepositories
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.Mockito
import javax.swing.tree.TreePath
import kotlin.reflect.KClass

class GitBranchesTreeActionsForSelectionTest : GitBranchesTreeTest() {
  private lateinit var repo1: MockGitRepository
  private lateinit var repo2: MockGitRepository

  private val branchesUiController = Mockito.mock(BranchesDashboardTreeController::class.java)

  override fun setUp() {
    super.setUp()
    repo1 = MockGitRepository(project, LightVirtualFile("repo-1"))
    repo2 = MockGitRepository(project, LightVirtualFile("repo-2"))
  }

  fun `test local branch actions`() = branchesTreeTest {
    singleRepoTreeState()
    select(BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "another"))

    assertActions(expected = listOf(
      isEnabledAndVisible<GitCheckoutAction>(),
      isEnabledAndVisible<GitCheckoutAsNewBranch>(),
      isEnabledAndVisible<GitCheckoutWithRebaseAction>(),
      isEnabledAndVisible<GitCheckoutWithUpdateAction>(),
      isEnabledAndVisible<GitCompareWithBranchAction>(),
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitRebaseBranchAction>(),
      isEnabledAndVisible<GitMergeRefAction>(),
      isEnabledAndVisible<GitUpdateSelectedBranchAction>(),
      isEnabledAndVisible<GitPushBranchAction>(),
      isEnabledAndVisible<GitTrackedBranchActionGroup>(),
      isEnabledAndVisible<GitRenameBranchAction>(),
      isEnabledAndVisible<GitDeleteRefAction>(),
    ))
  }

  fun `test select current branch actions`() = branchesTreeTest {
    singleRepoTreeState()
    select(BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "main"))

    assertActions(expected = listOf(
      isEnabledAndVisible<GitCheckoutAsNewBranch>(),
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitUpdateSelectedBranchAction>(),
      isEnabledAndVisible<GitPushBranchAction>(),
      isEnabledAndVisible<GitTrackedBranchActionGroup>(),
      isEnabledAndVisible<GitRenameBranchAction>(),
    ))
  }

  fun `test remote branch actions`() = branchesTreeTest {
    singleRepoTreeState()
    select(BranchesTreeNodeMatchers.branchMatcher(local = false))

    assertActions(expected = listOf(
      isEnabledAndVisible<GitCheckoutAction>(),
      isEnabledAndVisible<GitCheckoutAsNewBranch>(),
      isEnabledAndVisible<GitCheckoutWithRebaseAction>(),
      isEnabledAndVisible<GitCompareWithBranchAction>(),
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitRebaseBranchAction>(),
      isEnabledAndVisible<GitMergeRefAction>(),
      isEnabledAndVisible<GitPullBranchAction.WithRebase>(),
      isEnabledAndVisible<GitPullBranchAction.WithMerge>(),
      isEnabledAndVisible<GitDeleteRefAction>(),
    ))
  }

  fun `test tag actions`() = branchesTreeTest {
    singleRepoTreeState()
    select(BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Tag>())

    assertActions(expected = listOf(
      isEnabledAndVisible<GitCheckoutAction>(),
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitMergeRefAction>(),
      isEnabledAndVisible<GitPushTagActionWrapper>(),
      isEnabledAndVisible<GitPushTagActionWrapper>(),
      isEnabledAndVisible<GitDeleteRefAction>(),
    ))
  }

  fun `test current tag actions`() = branchesTreeTest {
    singleRepoTreeState()

    repo1.currentBranch = null
    repo1.state = Repository.State.DETACHED
    select(BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Tag>())
    val tagsHolderMock = Mockito.mock<GitRepositoryTagsHolder>()
    val currentRevisionHash = HashImpl.build(repo1.currentRevision!!)
    val tagsState = GitRepositoryTagsHolderImpl.LoadedState(
      tagsToCommitHashes = mapOf(GitTag("tag") to currentRevisionHash),
      commitHashesToTags = mapOf(currentRevisionHash to listOf(GitTag("tag")))
    )
    Mockito.`when`(tagsHolderMock.state).thenReturn(MutableStateFlow(tagsState))
    repo1.tagsHolder = tagsHolderMock

    assertActions(expected = listOf(
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitPushTagActionWrapper>(),
      isEnabledAndVisible<GitPushTagActionWrapper>(),
    ))
  }

  fun `test HEAD + branch selection`() = branchesTreeTest {
    singleRepoTreeState()

    select(
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Head>(),
      BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "another")
    )

    assertActions(expected = listOf(
      isEnabledAndVisible<BranchesDashboardActions.ShowArbitraryBranchesDiffAction>(),
      isEnabledAndVisible<BranchesDashboardActions.ShowArbitraryBranchesFileDiffAction>(),
    ))
  }

  fun `test current local + remote branch`() = branchesTreeTest {
    singleRepoTreeState()

    select(
      BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "main"),
      BranchesTreeNodeMatchers.branchMatcher(local = false)
    )

    assertActions(expected = listOf(
      isEnabledAndVisible<BranchesDashboardActions.ShowArbitraryBranchesDiffAction>(),
      isEnabledAndVisible<BranchesDashboardActions.ShowArbitraryBranchesFileDiffAction>(),
      isDisabledAndVisible<BranchesDashboardActions.DeleteBranchAction>(),
    ))
  }

  fun `test 3 branches`() = branchesTreeTest {
    singleRepoTreeState()

    select(
      BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "main"),
      BranchesTreeNodeMatchers.branchMatcher(local = true, shortName = "another"),
      BranchesTreeNodeMatchers.branchMatcher(local = false)
    )

    assertActions(expected = listOf(
      isDisabledAndVisible<BranchesDashboardActions.DeleteBranchAction>(),
    ))
  }

  fun `test origin node selected`() = branchesTreeTest {
    singleRepoTreeState()

    select(
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.RemoteGroup>()
    )

    Mockito.`when`(branchesUiController.getSelectedRemotes()).thenReturn(mapOf(repo1 to setOf(ORIGIN)))
    assertActions(expected = listOf(
      isEnabledAndVisible<BranchesDashboardActions.EditRemoteAction>(),
      isEnabledAndVisible<BranchesDashboardActions.RemoveRemoteAction>(),
    ))
  }

  fun `test nothing to show`() = branchesTreeTest {
    singleRepoTreeState()

    select(
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Branch>(),
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Group>(),
    )
    assertActions(expected = listOf())

    select(
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Head>(),
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Group>(),
    )
    assertActions(expected = listOf())

    select(
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.RemoteGroup>(),
      BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Branch>(),
    )
    assertActions(expected = listOf())
  }

  private fun GitBranchesTreeTestContext.assertActions(expected: List<ActionState>) {
    val rootActions = BranchActionsBuilder.build(simpleEvent())?.getChildren(simpleEvent())

    val actions = rootActions
      ?.flatMap { expandAction(it) }
      ?.mapNotNull { toActionState(it) }

    if (expected.isNotEmpty()) {
      assertNotNull(actions)
      assertEquals(expected.joinToString("\n"), actions!!.joinToString("\n"))
    }
    else {
      assertTrue("Expected no actions, but got:\n ${actions?.joinToString("\n")}", actions.isNullOrEmpty())
    }
  }

  private fun GitBranchesTreeTestContext.expandAction(action: AnAction): List<AnAction> {
    return when (action) {
      is GitTrackedBranchActionGroup -> listOf(action)
      is Separator -> emptyList()
      is ActionGroup -> action.getChildren(simpleEvent()).flatMap { expandAction(it) }
      else -> listOf(action)
    }
  }

  private fun GitBranchesTreeTestContext.toActionState(action: AnAction): ActionState? {
    val event = simpleEvent()
    action.update(event)
    return if (event.presentation.isVisible) {
      ActionState(event.presentation.isEnabled, action::class)
    }
    else {
      null
    }
  }

  private fun GitBranchesTreeTestContext.simpleEvent(): AnActionEvent {
    return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[PlatformDataKeys.PROJECT] = project
      sink[BRANCHES_UI_CONTROLLER] = branchesUiController

      BranchesDashboardTreeController.snapshotSelectionActionsKeys(sink, tree.selectionPaths)

      // Populate shared keys expected by some actions
      val selection = BranchesTreeSelection(tree.selectionPaths)
      val selectedNode = selection.selectedNodes.singleOrNull()
      if (selectedNode != null) {
        val selectedRepositories = getSelectedRepositories(selectedNode)
        val models = selectedRepositories.map { MockGitRepositoryModel(it) }
        sink[GitBranchesPopupKeys.AFFECTED_REPOSITORIES] = models
        sink[GitBranchesPopupKeys.SELECTED_REPOSITORY] = models.singleOrNull()
      }
    })
  }

  private fun GitBranchesTreeTestContext.select(vararg matchers: BranchesTreeNodeMatcher) {
    assertTrue(matchers.isNotEmpty())

    @Suppress("SSBasedInspection")
    TreeUtil.promiseSelect(tree, matchers.stream().map(BranchesTreeNodeMatchers::createMatcher))
    LOG.info("Current tree state:\n" + printTree())

    assertSize(matchers.size, tree.selectionPaths)
  }

  private fun GitBranchesTreeTestContext.singleRepoTreeState() {
    val localMain = GitLocalBranch("main")
    val localAnother = GitLocalBranch("another")
    val remoteOriginMain = GitStandardRemoteBranch(ORIGIN, "main")
    val remoteNotOriginMain = GitStandardRemoteBranch(NOT_ORIGIN, "main")
    val remoteAnother = GitStandardRemoteBranch(ORIGIN, "another")

    setRawState(
      localBranches = listOf(
        branchInfo(localMain, repositories = listOf(repo1), isCurrent = true),
        branchInfo(localAnother, repositories = listOf(repo1)),
        branchInfo(GitLocalBranch("prefix/another"), repositories = listOf(repo1))
      ),
      remoteBranches = listOf(
        branchInfo(remoteOriginMain, repositories = listOf(repo1)),
        branchInfo(remoteNotOriginMain, repositories = listOf(repo1)),
        branchInfo(remoteAnother, repositories = listOf(repo1)),
      ),
      tags = listOf(
        tagInfo(GitTag("tag"), repositories = listOf(repo1)),
      ),
      expanded = true,
    )

    repo1.currentBranch = GitLocalBranch("main")
    repo1.remotes = listOf(ORIGIN, NOT_ORIGIN)
    repo1.branchTrackInfos = listOf(GitBranchTrackInfo(localMain,
                                                       remoteOriginMain,
                                                       false),
                                    GitBranchTrackInfo(localAnother,
                                                       remoteAnother,
                                                       false))

    repo1.remoteBranchesWithHashes = listOf(remoteOriginMain, remoteNotOriginMain, remoteAnother).associateWith { HashImpl.build("0".repeat(40)) }
    repo1.updateWorkingTrees()
  }
}

private typealias BranchesTreeNodeMatcher = (BranchNodeDescriptor) -> Boolean

private object BranchesTreeNodeMatchers {
  fun branchMatcher(shortName: String? = null, local: Boolean? = null): BranchesTreeNodeMatcher = matcher@{
    if (it is BranchNodeDescriptor.Branch) {
      if (shortName != null && shortName != it.branchInfo.branchName) return@matcher false
      if (local != null && local != it.branchInfo.isLocalBranch) return@matcher false

      true
    }
    else false
  }

  inline fun <reified T : BranchNodeDescriptor> typeMatcher(): BranchesTreeNodeMatcher = {
    it is T
  }

  fun createMatcher(matcher: BranchesTreeNodeMatcher): TreeVisitor = object : TreeVisitor {
    override fun visit(path: TreePath): TreeVisitor.Action {
      val node = TreeUtil.getLastUserObject(path) as? BranchNodeDescriptor
                 ?: return TreeVisitor.Action.SKIP_CHILDREN
      return if (matcher(node)) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
    }
  }
}

private class ActionState(val isEnabled: Boolean, val action: KClass<out AnAction>) {
  companion object {
    inline fun <reified T : AnAction> isDisabledAndVisible() = ActionState(isEnabled = false, action = T::class)
    inline fun <reified T : AnAction> isEnabledAndVisible() = ActionState(isEnabled = true, action = T::class)
  }

  override fun toString(): String = "$action enabled=$isEnabled"
}
