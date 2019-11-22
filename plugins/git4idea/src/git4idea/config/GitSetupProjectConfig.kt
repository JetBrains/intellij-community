// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.util.concurrency.NonUrgentExecutor
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitSetupProjectConfig: StartupActivity.Background {
  override fun runActivity(project: Project) {
    ProjectLevelVcsManagerImpl.getInstanceImpl(project).addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        NonUrgentExecutor.getInstance().execute {
          setupConfigIfNeeded(project)
        }
      }
      else {
        setupConfigIfNeeded(project)
      }
    }
  }

  private fun setupConfigIfNeeded(project: Project) {
    val settings = GitVcsSettings.getInstance(project).state
    for (configVar in ConfigVariables.values()) {
      val settingsValue = configVar.settingsGetter(settings)
      if (settingsValue != null) {
        val rootsToUpdate = mutableListOf<GitRepository>()
        for (repo in GitRepositoryManager.getInstance(project).repositories) {
          val value = GitConfigUtil.getValue(project, repo.root, configVar.gitName)
          if (value == null) rootsToUpdate.add(repo)
        }

        for (repo in rootsToUpdate) {
          GitConfigUtil.setValue(project, repo.root, configVar.gitName, settingsValue, "--local")
        }
      }
    }
  }

  private enum class ConfigVariables(val gitName: String, val settingsGetter: (GitVcsOptions) -> String?) {
    GC_AUTO("gc.auto", { it.gcAuto }),
    CORE_LONGPATHS("core.longpaths", { if (SystemInfo.isWindows) it.coreLongpaths else null })
  }
}

