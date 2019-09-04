// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.util.concurrency.NonUrgentExecutor
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

const val GC_AUTO = "gc.auto"

class GitSetupProjectConfig: StartupActivity.Background {
  override fun runActivity(project: Project) {
    ProjectLevelVcsManagerImpl.getInstanceImpl(project).addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        NonUrgentExecutor.getInstance().execute {
          setupGcAutoIfNeeded(project)
        }
      }
      else {
        setupGcAutoIfNeeded(project)
      }
    }
  }

  private fun setupGcAutoIfNeeded(project: Project) {
    val gcAuto = GitVcsSettings.getInstance(project).state.gcAuto
    if (gcAuto != null) {
      val rootsToUpdate = mutableListOf<GitRepository>()
      for (repo in GitRepositoryManager.getInstance(project).repositories) {
        val value = GitConfigUtil.getValue(project, repo.root, GC_AUTO)
        if (value == null) rootsToUpdate.add(repo)
      }

      for (repo in rootsToUpdate) {
        GitConfigUtil.setValue(project, repo.root, GC_AUTO, gcAuto, "--local")
      }
    }
  }
}
