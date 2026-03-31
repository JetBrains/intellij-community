// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import git4idea.actions.workingTree.GitCreateWorkingTreeAction
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.actions.workingTree.ShowWorkingTreesAction
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.update.GitSubmoduleTestBase
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

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

  private fun ensureChangesToolWindowRegistered() {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    if (toolWindowManager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) == null) {
      toolWindowManager.registerToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) {}
    }
  }

  private fun createActionEvent(currentRepository: GitRepository? = null) =
    TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[PlatformDataKeys.PROJECT] = project
      if (currentRepository != null) {
        sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = currentRepository
      }
    })
}
