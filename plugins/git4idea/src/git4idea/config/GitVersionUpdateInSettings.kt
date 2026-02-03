// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
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
      val presentation = e.presentation

      if (project == null) {
        presentation.isEnabledAndVisible = false
        return
      }

      val versionChecker = project.service<GitNewVersionChecker>()
      val versionToUpdate = versionChecker.newAvailableVersion
      val gitNotInstalled = versionChecker.gitNotInstalled

      presentation.text =
        if (gitNotInstalled) GitBundle.message("git.executable.install.available", versionToUpdate.presentation)
        else GitBundle.message("git.executable.new.version.update.available", versionToUpdate.presentation)

      presentation.isEnabledAndVisible = isUpdateSupported(versionToUpdate)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val versionChecker = project.service<GitNewVersionChecker>()
      val versionToUpdate = versionChecker.newAvailableVersion
      if (versionToUpdate.isNotNull) {
        downloadAndInstallGit(project, onSuccess = {
          versionChecker.reset()
          markAsRead()
        })
      }
    }
  }
}

internal class GitNewVersionCheckerStarter(private val project: Project) : VcsListener {
  override fun directoryMappingChanged() {
    if (afterIdleCheckRegistryValue >= 0) {
      project.service<GitNewVersionChecker>().restart()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class GitNewVersionChecker(private val project: Project, private val cs: CoroutineScope) {

  @Volatile
  var newAvailableVersion: GitVersion = GitVersion.NULL
    private set

  @Volatile
  var gitNotInstalled: Boolean = true
    private set

  private val job = AtomicReference<Job?>(null)

  fun restart() {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    vcsManager.runAfterInitialization {
      val haveGitMappings = vcsManager.getDirectoryMappings(GitVcs.getInstance(project)).isNotEmpty()
      job.getAndSet(
        if (haveGitMappings) checkNewVersionPeriodicallyOnIdle(project, cs) else null
      )?.cancel()
    }
  }

  internal fun reset() {
    newAvailableVersion = GitVersion.NULL
    gitNotInstalled = true
  }

  @OptIn(FlowPreview::class)
  private fun checkNewVersionPeriodicallyOnIdle(project: Project, cs: CoroutineScope): Job {
    return cs.launch(CoroutineName("Git update present check")) {
      IdleTracker.getInstance().events
        .debounce(afterIdleCheckRegistryValue.minutes)
        .collect {
          withContext(Dispatchers.IO) {
            if (newAvailableVersion.isNotNull) return@withContext

            val currentVersion = GitExecutableManager.getInstance().getVersionOrIdentifyIfNeeded(project)
            gitNotInstalled = currentVersion.isNull

            if (!isUpdateSupported(currentVersion)) {
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

private fun isUpdateSupported(version: GitVersion): Boolean {
  return when (version.type) {
    GitVersion.Type.MSYS -> true
    GitVersion.Type.CYGWIN -> true
    else -> false
  }
}

private val afterIdleCheckRegistryValue get() = Registry.intValue("git.version.check.minutes")
private val GitVersion?.isNotNull get() = this != null && !this.isNull
