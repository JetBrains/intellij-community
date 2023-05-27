// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

internal class GitSetupProjectConfig : ProjectActivity {
  override suspend fun execute(project: Project) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      setupConfigIfNeeded(project)
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
    CORE_FS_MONITOR("core.fsmonitor", { it.coreFsMonitor }),
    CORE_UNTRACKED_CACHE("core.untrackedcache", { it.coreUntrackedCache }),
    CORE_LONGPATHS("core.longpaths", { if (SystemInfo.isWindows) it.coreLongpaths else null }),
    FEATURE_MANY_FILES("feature.manyFiles", { it.featureManyFiles }),
  }
}
