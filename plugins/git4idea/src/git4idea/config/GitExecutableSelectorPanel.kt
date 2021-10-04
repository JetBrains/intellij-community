// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.ui.layout.*
import com.intellij.util.ui.VcsExecutablePathSelector
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.CalledInAny

internal class GitExecutableSelectorPanel(val project: Project, val disposable: Disposable) {
  companion object {
    fun RowBuilder.createGitExecutableSelectorRow(project: Project, disposable: Disposable) {
      val panel = GitExecutableSelectorPanel(project, disposable)
      with(panel) {
        createRow()
      }
    }
  }

  private val applicationSettings get() = GitVcsApplicationSettings.getInstance()
  private val projectSettings get() = GitVcsSettings.getInstance(project)

  @Volatile
  private var versionCheckRequested = false

  private val pathSelector =
    VcsExecutablePathSelector(GitVcs.DISPLAY_NAME.get(), disposable, object : VcsExecutablePathSelector.ExecutableHandler {
      override fun patchExecutable(executable: String): String? {
        return GitExecutableDetector.patchExecutablePath(executable)
      }

      override fun testExecutable(executable: String) {
        testGitExecutable(executable)
      }
    })

  private fun testGitExecutable(pathToGit: String) {
    val modalityState = ModalityState.stateForComponent(pathSelector.mainPanel)
    val errorNotifier = InlineErrorNotifierFromSettings(
      GitExecutableInlineComponent(pathSelector.errorComponent, modalityState, null),
      modalityState, disposable
    )

    object : Task.Modal(project, GitBundle.message("git.executable.version.progress.title"), true) {
      private lateinit var gitVersion: GitVersion

      override fun run(indicator: ProgressIndicator) {
        val executableManager = GitExecutableManager.getInstance()
        val executable = executableManager.getExecutable(pathToGit)
        executableManager.dropVersionCache(executable)
        gitVersion = executableManager.identifyVersion(executable)
      }

      override fun onThrowable(error: Throwable) {
        val problemHandler = findGitExecutableProblemHandler(project)
        problemHandler.showError(error, errorNotifier)
      }

      override fun onSuccess() {
        if (gitVersion.isSupported) {
          errorNotifier.showMessage(GitBundle.message("git.executable.version.is", gitVersion.presentation))
        }
        else {
          showUnsupportedVersionError(project, gitVersion, errorNotifier)
        }
      }
    }.queue()
  }

  private inner class InlineErrorNotifierFromSettings(inlineComponent: InlineComponent,
                                                      private val modalityState: ModalityState,
                                                      disposable: Disposable) :
    InlineErrorNotifier(inlineComponent, modalityState, disposable) {
    @CalledInAny
    override fun showError(text: String, description: String?, fixOption: ErrorNotifier.FixOption?) {
      if (fixOption is ErrorNotifier.FixOption.Configure) {
        super.showError(text, description, null)
      }
      else {
        super.showError(text, description, fixOption)
      }
    }

    override fun resetGitExecutable() {
      super.resetGitExecutable()
      GitExecutableManager.getInstance().getDetectedExecutable(project) // populate cache
      invokeAndWaitIfNeeded(modalityState) {
        resetPathSelector()
      }
    }
  }

  private fun getCurrentExecutablePath(): String? = pathSelector.currentPath?.takeIf { it.isNotBlank() }

  private fun RowBuilder.createRow() = row {
    pathSelector.mainPanel(growX)
      .onReset {
        resetPathSelector()
      }
      .onIsModified {
        val projectSettingsPathToGit = projectSettings.pathToGit
        val currentPath = getCurrentExecutablePath()
        if (pathSelector.isOverridden) {
          currentPath != projectSettingsPathToGit
        }
        else {
          currentPath != applicationSettings.savedPathToGit || projectSettingsPathToGit != null
        }
      }
      .onApply {
        val executablePathOverridden = pathSelector.isOverridden
        val currentPath = getCurrentExecutablePath()
        if (executablePathOverridden) {
          projectSettings.pathToGit = currentPath
        }
        else {
          applicationSettings.setPathToGit(currentPath)
          projectSettings.pathToGit = null
        }

        validateExecutableOnceAfterClose()
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      }
  }

  private fun resetPathSelector() {
    val projectSettingsPathToGit = projectSettings.pathToGit
    val detectedExecutable = try {
      GitExecutableManager.getInstance().getDetectedExecutable(project)
    }
    catch (e: ProcessCanceledException) {
      GitExecutableDetector.getDefaultExecutable()
    }
    pathSelector.reset(applicationSettings.savedPathToGit,
      projectSettingsPathToGit != null,
      projectSettingsPathToGit,
      detectedExecutable)
  }

  /**
   * Special method to check executable after it has been changed through settings
   */
  private fun validateExecutableOnceAfterClose() {
    if (versionCheckRequested) return
    versionCheckRequested = true

    runInEdt(ModalityState.NON_MODAL) {
      versionCheckRequested = false

      runBackgroundableTask(GitBundle.message("git.executable.version.progress.title"), project, true) {
        GitExecutableManager.getInstance().testGitExecutableVersionValid(project)
      }
    }
  }
}