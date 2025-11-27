// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
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
  fun getSingleRepositoryInProjectRootOrNull(): GitRepository? {
    val projectRoot = getOnlyProjectRootOrNull()
    if (projectRoot == null) {
      return null
    }
    val repositories = GitRepositoryManager.getInstance(project).repositories
    val repository = repositories.singleOrNull() ?: return null
    if (projectRoot != repository.root) {
      return null
    }
    return repository
  }

  private fun getOnlyProjectRootOrNull(): VirtualFile? {
    val baseDirectories = project.getBaseDirectories()
    return baseDirectories.singleOrNull()
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
}