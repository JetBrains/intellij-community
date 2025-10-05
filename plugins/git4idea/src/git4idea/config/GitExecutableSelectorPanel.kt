// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.application
import com.intellij.util.ui.VcsExecutablePathSelector
import com.intellij.vcs.git.GitDisplayName
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.CalledInAny

internal class GitExecutableSelectorPanel(val project: Project, val disposable: Disposable) {
  companion object {
    fun Panel.createGitExecutableSelectorRow(project: Project, disposable: Disposable) {
      val panel = GitExecutableSelectorPanel(project, disposable)
      with(panel) {
        createRow()
      }
    }
  }

  private val applicationSettings get() = GitVcsApplicationSettings.getInstance()
  private val projectSettings get() = GitVcsSettings.getInstance(project)

  private val pathSelector = VcsExecutablePathSelector(GitDisplayName.NAME, disposable, GitExecutableHandler())

  @Volatile
  private var versionCheckRequested = false

  init {
    application.messageBus.connect(disposable).subscribe(GitExecutableManager.TOPIC,
      GitExecutableListener { runInEdt(getModalityState()) { resetPathSelector() } })

    BackgroundTaskUtil.executeOnPooledThread(disposable) {
      GitExecutableManager.getInstance().getDetectedExecutable(project, true) // detect executable if needed
    }
  }

  private fun Panel.createRow() = row {
    cell(pathSelector.mainPanel)
      .align(AlignX.FILL)
      .onReset {
        resetPathSelector()
      }
      .onIsModified {
        pathSelector.isModified(
          applicationSettings.savedPathToGit,
          projectSettings.pathToGit != null,
          projectSettings.pathToGit)
      }
      .onApply {
        val currentPath = pathSelector.currentPath
        if (pathSelector.isOverridden) {
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
    pathSelector.setAutoDetectedPath(GitExecutableManager.getInstance().getDetectedExecutable(project, false))
    pathSelector.reset(
      applicationSettings.savedPathToGit,
      projectSettings.pathToGit != null,
      projectSettings.pathToGit)
  }

  private fun testGitExecutable(pathToGit: String) {
    val modalityState = getModalityState()
    val errorNotifier = InlineErrorNotifierFromSettings(
      GitExecutableInlineComponent(pathSelector.errorComponent, modalityState, null),
      modalityState, disposable
    )

    if (!project.isDefault && !TrustedProjects.isProjectTrusted(project)) {
      errorNotifier.showError(GitBundle.message("git.executable.validation.cant.run.in.safe.mode"), null)
      return
    }

    object : Task.Modal(project, GitBundle.message("git.executable.version.progress.title"), true) {
      private lateinit var gitVersion: GitVersion

      override fun run(indicator: ProgressIndicator) {
        val executableManager = GitExecutableManager.getInstance()
        val executable = executableManager.getExecutable(project, pathToGit)
        executableManager.dropVersionCache(executable)
        gitVersion = executableManager.identifyVersion(project, executable)
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

  /**
   * Special method to check executable after it has been changed through settings
   */
  private fun validateExecutableOnceAfterClose() {
    if (versionCheckRequested) return
    versionCheckRequested = true

    runInEdt(ModalityState.nonModal()) {
      versionCheckRequested = false

      runBackgroundableTask(GitBundle.message("git.executable.version.progress.title"), project, true) {
        GitExecutableManager.getInstance().testGitExecutableVersionValid(project)
      }
    }
  }

  private fun getModalityState() = ModalityState.stateForComponent(pathSelector.mainPanel)

  private inner class InlineErrorNotifierFromSettings(inlineComponent: InlineComponent,
                                                      private val modalityState: ModalityState,
                                                      disposable: Disposable)
    : InlineErrorNotifier(inlineComponent, modalityState, disposable) {
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

      GitExecutableManager.getInstance().getDetectedExecutable(project, true) // populate cache
      invokeAndWaitIfNeeded(modalityState) {
        resetPathSelector()
      }
    }
  }

  private inner class GitExecutableHandler : VcsExecutablePathSelector.ExecutableHandler {
    override fun patchExecutable(executable: String): String? {
      return GitExecutableDetector.patchExecutablePath(project, executable)
    }

    override fun testExecutable(executable: String) {
      testGitExecutable(executable)
    }
  }
}