// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.ide.IdleTracker
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import git4idea.i18n.GitBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.minutes

internal class GitVersionUpdateSettingsEntryProvider : SettingsEntryPointAction.ActionProvider {
  override fun getUpdateActions(context: DataContext): Collection<SettingsEntryPointAction.UpdateAction> {
    return listOf(UpdateAction)
  }

  object UpdateAction : SettingsEntryPointAction.UpdateAction() {

    @Volatile
    private var updateAvailable = false

    override fun markAsRead() {
      updateAvailable = false
    }

    fun markUpdateAvailable() {
      updateAvailable = true
    }

    override fun isNewAction(): Boolean = updateAvailable

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val project = e.project
      val versionToUpdate = project?.service<GitNewVersionChecker>()?.newAvailableVersion
      val presentation = e.presentation

      if (project == null || versionToUpdate == null || versionToUpdate.isNull || versionToUpdate.isWSL) {
        presentation.isEnabledAndVisible = false
      }
      else {
        presentation.text = GitBundle.message("git.executable.new.version.update.available", versionToUpdate.presentation)
        presentation.isEnabledAndVisible = true
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val versionToUpdate = project.service<GitNewVersionChecker>().newAvailableVersion
      if (versionToUpdate.isNotNull) {
        downloadAndInstallGit(project, onSuccess = ::markAsRead)
      }
    }
  }
}

@Service(Service.Level.PROJECT)
internal class GitNewVersionChecker(project: Project, cs: CoroutineScope) {
  class Starter : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (afterIdleCheckRegistryValue >= 0) {
        project.service<GitNewVersionChecker>()
      }
    }
  }

  @Volatile
  var newAvailableVersion: GitVersion? = null
    private set

  init {
    checkNewVersionPeriodicallyOnIdle(project, cs)
  }

  @OptIn(FlowPreview::class)
  private fun checkNewVersionPeriodicallyOnIdle(project: Project, cs: CoroutineScope) {
    cs.launch(CoroutineName("Git update present check")) {
      IdleTracker.getInstance().events
        .debounce(afterIdleCheckRegistryValue.minutes)
        .collect {
          withContext(Dispatchers.IO) {
            if (newAvailableVersion.isNotNull) return@withContext

            val currentVersion = GitExecutableManager.getInstance().getVersionOrIdentifyIfNeeded(project)

            if (currentVersion.isWSL) {
              newAvailableVersion = currentVersion
              return@withContext
            }

            val latestVersion = getLatestAvailableVersion()

            if (latestVersion > currentVersion) {
              newAvailableVersion = latestVersion
              withContext(Dispatchers.EDT) {
                GitVersionUpdateSettingsEntryProvider.UpdateAction.markUpdateAvailable()
                SettingsEntryPointAction.updateState()
              }
            }
          }
        }
    }
  }
}

private val afterIdleCheckRegistryValue get() = Registry.intValue("git.version.check.minutes")
private val GitVersion?.isNotNull get() = this != null && !this.isNull
