// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.*
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.containers.stream
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.actions.branch.*
import git4idea.actions.ref.GitCheckoutAction
import git4idea.actions.ref.GitDeleteRefAction
import git4idea.actions.ref.GitMergeRefAction
import git4idea.actions.ref.GitShowDiffWithRefAction
import git4idea.branch.ActionState.Companion.isDisabledAndVisible
import git4idea.branch.ActionState.Companion.isEnabledAndVisible
import git4idea.branch.GitBranchesTreeTestContext.Companion.NOT_ORIGIN
import git4idea.branch.GitBranchesTreeTestContext.Companion.ORIGIN
import git4idea.branch.GitBranchesTreeTestContext.Companion.branchInfo
import git4idea.branch.GitBranchesTreeTestContext.Companion.tagInfo
import git4idea.repo.GitTagHolder
import git4idea.test.MockGitRepository
import git4idea.ui.branch.dashboard.*
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchActionsBuilder
import org.mockito.Mockito
import javax.swing.tree.TreePath
import kotlin.reflect.KClass

class GitBranchesTreeActionsForSelectionTest : GitBranchesTreeTest() {
  private lateinit var repo1: MockGitRepository
  private lateinit var repo2: MockGitRepository

  private val branchesUiController = Mockito.mock(BranchesDashboardController::class.java)

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
      isEnabledAndVisible<GitCompareWithBranchAction>(),
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
      isEnabledAndVisible<GitRebaseBranchAction>(),
      isEnabledAndVisible<GitMergeRefAction>(),
      isEnabledAndVisible<GitPushBranchAction>(),
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
      isEnabledAndVisible<GitPushBranchAction>(),
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
      isEnabledAndVisible<GitDeleteRefAction>(),
    ))
  }

  fun `test current tag actions`() = branchesTreeTest {
    singleRepoTreeState()

    repo1.currentBranch = null
    repo1.state = Repository.State.DETACHED
    select(BranchesTreeNodeMatchers.typeMatcher<BranchNodeDescriptor.Tag>())
    val tagsHolderMock = Mockito.mock<GitTagHolder>()
    repo1.tagHolder = tagsHolderMock
    Mockito.`when`(tagsHolderMock.getTag(repo1.currentRevision!!)).thenReturn(GitTag("tag"))

    assertActions(expected = listOf(
      isEnabledAndVisible<GitShowDiffWithRefAction>(),
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
    val actions = BranchActionsBuilder.build(simpleEvent())?.getChildren(simpleEvent())?.mapNotNull {
      if (it is Separator) return@mapNotNull null
      val event = simpleEvent()
      it.update(event)
      if (!event.presentation.isVisible) return@mapNotNull null
      ActionState(event.presentation.isEnabled, it::class)
    }

    if (expected.isNotEmpty()) {
      assertNotNull(actions)
      assertEquals(expected.joinToString("\n"), actions!!.joinToString("\n"))
    }
    else {
      assertTrue("Expected no actions, but got:\n ${actions?.joinToString("\n")}", actions.isNullOrEmpty())
    }
  }


  private fun GitBranchesTreeTestContext.simpleEvent(): AnActionEvent {
    return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[PlatformDataKeys.PROJECT] = project
      sink[BRANCHES_UI_CONTROLLER] = branchesUiController
      BranchesDashboardUi.snapshotSelectionActionsKeys(sink, tree.selectionPaths)
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
    setRawState(
      localBranches = listOf(
        branchInfo(GitLocalBranch("main"), repositories = listOf(repo1), isCurrent = true),
        branchInfo(GitLocalBranch("another"), repositories = listOf(repo1)),
        branchInfo(GitLocalBranch("prefix/another"), repositories = listOf(repo1))
      ),
      remoteBranches = listOf(
        branchInfo(GitStandardRemoteBranch(ORIGIN, "main"), repositories = listOf(repo1)),
        branchInfo(GitStandardRemoteBranch(NOT_ORIGIN, "main"), repositories = listOf(repo1)),
      ),
      tags = listOf(
        tagInfo(GitTag("tag"), repositories = listOf(repo1)),
      ),
      expanded = true,
    )

    repo1.currentBranch = GitLocalBranch("main")
    repo1.remotes = listOf(ORIGIN, NOT_ORIGIN)
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
