// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {

  companion object {
    private const val WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY: String = "Git.Working.Tree.Tab.closed.by.user"

    fun getInstance(project: Project): GitWorkingTreesService = project.getService(GitWorkingTreesService::class.java)

    /**
     * So far only the `single repository` case is supported for working trees
     */
    fun getSingleRepositoryOrNullIfEnabled(project: Project?): GitRepository? {
      if (project == null) return null
      if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return null
      val repositories = GitRepositoryManager.getInstance(project).repositories
      return repositories.singleOrNull()
    }
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    if (getSingleRepositoryOrNullIfEnabled(project) == null) {
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