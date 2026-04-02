// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.TestActionEvent
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitCreateWorkingTreeAction
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.actions.workingTree.ShowWorkingTreesAction
import git4idea.workingTrees.ui.CheckoutWorkingTreeAction
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.update.GitSubmoduleTestBase
import git4idea.workingTrees.GitWorkingTreeTestBase.Companion.ensureWorkingTreesUpToDateForTests
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.io.path.invariantSeparatorsPathString

class GitWorkingTreesServiceTest : GitSubmoduleTestBase() {

  fun testSingleRepositoryProject() {
    val mainRepo = createProjectRepository()
    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isEqualTo(mainRepo)
  }

  fun testFeatureDisabled() {
    createProjectRepository()
    enableWorkingTreesFeature(false)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isNull()
  }

  fun testMonorepoProject() {
    val mainRepo = createProjectRepository()
    createIndependentRepository(projectNioRoot.resolve("modules/app"))

    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isEqualTo(mainRepo)
  }

  fun testProjectWithSubmodules() {
    val mainRepo = createProjectRepositoryWithRemote()
    addSubmoduleInProject("libs/module")

    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isEqualTo(mainRepo)
  }

  fun testMultipleUnrelatedRepositories() {
    createIndependentRepository(projectNioRoot.resolve("project1"))
    createIndependentRepository(projectNioRoot.resolve("project2"))

    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isNull()
  }

  fun testRepositoriesForMultipleRoots() {
    val repo1 = createIndependentRepository(projectNioRoot.resolve("project1"))
    val repo2 = createIndependentRepository(projectNioRoot.resolve("project2"))

    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepositoriesForWorkingTreesSupport(project)

    assertThat(result).containsExactlyInAnyOrder(repo1, repo2)
  }

  fun testNestedSubmodules() {
    val mainRepo = createProjectRepositoryWithRemote()
    val submoduleA = createPlainRepo("a")
    val submoduleB = createPlainRepo("b")
    addSubmodule(submoduleA.local, submoduleB.remote, "b")
    addSubmodule(projectNioRoot, submoduleA.remote, "a")
    mainRepo.git("submodule update --init --recursive")

    registerProjectRepository(projectNioRoot.resolve("a"))
    registerProjectRepository(projectNioRoot.resolve("a/b"))
    updateAllRepositories()

    enableWorkingTreesFeature(true)

    val result = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)

    assertThat(result).isEqualTo(mainRepo)
  }

  fun testShowActionForSingleRepository() {
    createProjectRepository()
    enableWorkingTreesFeature(true)
    ensureChangesToolWindowRegistered()

    val action = ShowWorkingTreesAction()
    val event = createActionEvent()
    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  fun testShowActionForMonorepo() {
    createProjectRepository()
    createIndependentRepository(projectNioRoot.resolve("module"))
    enableWorkingTreesFeature(true)
    ensureChangesToolWindowRegistered()

    val action = ShowWorkingTreesAction()
    val event = createActionEvent()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  fun testShowActionForMultipleRepositories() {
    createIndependentRepository(projectNioRoot.resolve("proj1"))
    createIndependentRepository(projectNioRoot.resolve("proj2"))
    enableWorkingTreesFeature(true)
    ensureChangesToolWindowRegistered()

    val action = ShowWorkingTreesAction()
    val event = createActionEvent()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  fun testCreateActionForSelectedRepositoryInWorktreesTab() {
    val repository = createIndependentRepository(projectNioRoot.resolve("proj1"))
    createIndependentRepository(projectNioRoot.resolve("proj2"))
    enableWorkingTreesFeature(true)

    val action = GitCreateWorkingTreeAction()
    val event = createActionEvent(repository)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  fun testCheckoutAction() {
    val repository = createProjectRepository()
    val worktree = createLinkedWorktree(repository, "feature", "feature-tree")
    enableWorkingTreesFeature(true)

    val action = CheckoutWorkingTreeAction()
    val event = createActionEvent(repository, listOf(worktree))

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  fun testHideCheckoutForDetached() {
    val repository = createProjectRepository()
    val worktree = createDetachedWorktree(repository, "feature", "detached-tree")
    enableWorkingTreesFeature(true)

    val action = CheckoutWorkingTreeAction()
    val event = createActionEvent(repository, listOf(worktree))

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  fun testCheckoutWorktreeBranch() {
    val repository = createProjectRepository()
    val worktree = createLinkedWorktree(repository, "feature", "feature-tree")
    enableWorkingTreesFeature(true)

    val result = runBlocking {
      GitWorkingTreesService.getInstance(project).checkoutWorktreeInCurrentRepositoryInternal(repository, worktree)
    }

    repository.update()
    assertThat(result).isTrue()
    assertThat(repository.currentBranchName).isEqualTo("feature")
  }

  fun testConfirmForceCheckout() {
    val repository = createProjectRepository()
    val worktreePath = testNioRoot.resolve("feature-tree")
    val worktree = createLinkedWorktree(repository, "feature", "feature-tree")
    enableWorkingTreesFeature(true)

    cd(worktreePath)
    Files.writeString(worktreePath.resolve("a.txt"), "feature")
    git("add a.txt")
    git("commit -m feature")
    cd(projectNioRoot)
    Files.writeString(projectNioRoot.resolve("a.txt"), "local changes")

    var confirmationShown = false
    TestDialogManager.setTestDialog(TestDialog { message ->
      confirmationShown = true
      Messages.YES
    })

    val result = try {
      runBlocking {
        GitWorkingTreesService.getInstance(project).checkoutWorktreeInCurrentRepositoryInternal(repository, worktree)
      }
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }

    repository.update()
    assertThat(result).isTrue()
    assertThat(confirmationShown).isTrue()
    assertThat(repository.currentBranchName).isEqualTo("feature")
    assertThat(Files.readString(projectNioRoot.resolve("a.txt"))).isEqualTo("feature")
  }

  fun testCancelForceCheckout() {
    val repository = createProjectRepository()
    val worktreePath = testNioRoot.resolve("feature-tree")
    val worktree = createLinkedWorktree(repository, "feature", "feature-tree")
    enableWorkingTreesFeature(true)

    cd(worktreePath)
    Files.writeString(worktreePath.resolve("a.txt"), "feature")
    git("add a.txt")
    git("commit -m feature")
    cd(projectNioRoot)
    Files.writeString(projectNioRoot.resolve("a.txt"), "local changes")

    var confirmationShown = false
    TestDialogManager.setTestDialog(TestDialog { message ->
      confirmationShown = true
      Messages.NO
    })

    val result = try {
      runBlocking {
        GitWorkingTreesService.getInstance(project).checkoutWorktreeInCurrentRepositoryInternal(repository, worktree)
      }
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }

    repository.update()
    assertThat(result).isFalse()
    assertThat(confirmationShown).isTrue()
    assertThat(repository.currentBranchName).isEqualTo("master")
    assertThat(Files.readString(projectNioRoot.resolve("a.txt"))).isEqualTo("local changes")
  }

  private fun enableWorkingTreesFeature(enabled: Boolean) {
    Registry.get("git.enable.working.trees.feature").setValue(enabled, testRootDisposable)
  }

  private fun createProjectRepository(): GitRepository {
    val repository = createRepository(project, projectNioRoot, true)
    updateAllRepositories()
    return repository
  }

  private fun createProjectRepositoryWithRemote(): GitRepository {
    val repository = createProjectRepository()
    prepareRemoteRepo(repository)
    repository.git("push -u origin master")
    return repository
  }

  private fun createIndependentRepository(root: Path): GitRepository {
    val repository = createRepository(project, root, true)
    updateAllRepositories()
    return repository
  }

  private fun addSubmoduleInProject(relativePath: String): GitRepository {
    val submodule = createPlainRepo(relativePath.replace('/', '-'))
    addSubmodule(projectNioRoot, submodule.remote, relativePath)
    return registerProjectRepository(projectNioRoot.resolve(relativePath))
  }

  private fun registerProjectRepository(root: Path): GitRepository {
    val repository = registerRepo(project, root)
    updateAllRepositories()
    return repository
  }

  private fun updateAllRepositories() {
    GitRepositoryManager.getInstance(project).updateAllRepositories()
  }

  private fun createLinkedWorktree(repository: GitRepository, branchName: String, worktreeDirName: String): GitWorkingTree {
    return createWorktree(repository, branchName, worktreeDirName, detached = false)
  }

  private fun createDetachedWorktree(repository: GitRepository, branchName: String, worktreeDirName: String): GitWorkingTree {
    return createWorktree(repository, branchName, worktreeDirName, detached = true)
  }

  private fun createWorktree(repository: GitRepository, branchName: String, worktreeDirName: String, detached: Boolean): GitWorkingTree {
    val worktreePath = testNioRoot.resolve(worktreeDirName)
    repository.git("worktree add -B $branchName ${worktreePath.invariantSeparatorsPathString}")
    if (detached) {
      cd(worktreePath)
      git("checkout --detach")
      cd(projectNioRoot)
    }
    repository.ensureWorkingTreesUpToDateForTests()
    return repository.workingTreeHolder.getWorkingTrees().single { it.path.path.replace('\\', '/') == worktreePath.invariantSeparatorsPathString }
  }

  private fun ensureChangesToolWindowRegistered() {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    if (toolWindowManager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) == null) {
      toolWindowManager.registerToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) {}
    }
  }

  private fun createActionEvent(currentRepository: GitRepository? = null, selectedWorkingTrees: List<GitWorkingTree>? = null) =
    TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[PlatformDataKeys.PROJECT] = project
      if (currentRepository != null) {
        sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = currentRepository
      }
      if (selectedWorkingTrees != null) {
        sink[GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES] = selectedWorkingTrees
      }
    })
}
