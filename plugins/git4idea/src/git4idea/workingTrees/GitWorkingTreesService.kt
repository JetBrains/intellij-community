// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {

  companion object {
    private const val WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY: String = "Git.Working.Tree.Tab.closed.by.user"

    fun getInstance(project: Project): GitWorkingTreesService = project.getService(GitWorkingTreesService::class.java)
  }

  /**
   * So far only the case `single repository in project root` is supported for working trees
   */
  fun getSingleRepositoryInProjectRootOrNull(): GitRepositoryModel? {
    val baseDirectories = project.getBaseDirectories()
    if (baseDirectories.size != 1) {
      return null
    }
    val projectRoot = baseDirectories.first()
    val holder = GitRepositoriesHolder.getInstance(project)
    if (!holder.initialized) {
      return null
    }
    val repositoryModels = holder.getAll()
    if (repositoryModels.isEmpty()) {
      return null
    }
    if (repositoryModels.size > 1) {
      return null
    }

    val model = repositoryModels[0]
    val modelRoot = model.root.virtualFile ?: return null
    if (modelRoot != projectRoot) {
      return null
    }
    return model
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) {
      return false
    }
    if (getSingleRepositoryInProjectRootOrNull() == null) {
      return false
    }
    return !PropertiesComponent.getInstance(project).getBoolean(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY, false)
  }

  fun workingTreesTabOpenedByUser() {
    PropertiesComponent.getInstance(project).unsetValue(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY)
  }

  fun workingTreesTabClosedByUser() {
    PropertiesComponent.getInstance(project).setValue(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY, true)
  }

  fun reloadRepository(repositoryId: RepositoryId) {
    val repositoryModel = GitRepositoriesHolder.getInstance(project).get(repositoryId) ?: return
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(repositoryModel.root) ?: return
    repository.workingTreeHolder.reload()
  }
}