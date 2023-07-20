// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlin.reflect.KProperty1

internal class GitSetupProjectConfig : ProjectActivity {
  override suspend fun execute(project: Project): Unit = blockingContext {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      setupConfigIfNeeded(project)
    }
  }

  private fun setupConfigIfNeeded(project: Project) {
    val settings = GitVcsSettings.getInstance(project).state
    for (configVar in ConfigVariables.values()) {
      setupConfigIfNeeded(project, configVar, settings)
    }
  }

  private fun setupConfigIfNeeded(project: Project,
                                  configVar: ConfigVariables,
                                  settings: GitVcsOptions) {
    val settingsValue = configVar.settingsGetter(settings) ?: return

    if (!configVar.condition(project, settingsValue)) {
      return
    }

    val rootsToUpdate = mutableListOf<GitRepository>()
    for (repo in GitRepositoryManager.getInstance(project).repositories) {
      val value = GitConfigUtil.getValue(project, repo.root, configVar.gitName)
      if (value == null) rootsToUpdate.add(repo)
    }

    for (repo in rootsToUpdate) {
      GitConfigUtil.setValue(project, repo.root, configVar.gitName, settingsValue, "--local")
    }
  }

  private enum class ConfigVariables(val gitName: String,
                                     val settingsGetter: KProperty1<GitVcsOptions, String?>,
                                     val condition: (project: Project, value: String) -> Boolean = { _, _ -> true }) {
    GC_AUTO("gc.auto", GitVcsOptions::gcAuto),
    CORE_FS_MONITOR("core.fsmonitor", GitVcsOptions::coreFsMonitor, { project, value ->
      when {
        GitConfigUtil.getBooleanValue(value) != null -> GitVersionSpecialty.SUPPORTS_BOOLEAN_FSMONITOR_OPTION.existsIn(project)
        else -> true
      }
    }),
    CORE_UNTRACKED_CACHE("core.untrackedcache", GitVcsOptions::coreUntrackedCache),
    CORE_LONGPATHS("core.longpaths", GitVcsOptions::coreLongpaths, { _, _ -> SystemInfo.isWindows }),
    FEATURE_MANY_FILES("feature.manyFiles", GitVcsOptions::featureManyFiles),
  }
}
