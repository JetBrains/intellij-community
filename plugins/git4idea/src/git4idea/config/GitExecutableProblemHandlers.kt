// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import git4idea.GitVcs
import git4idea.config.GitExecutableProblemsNotifier.getPrettyErrorMessage
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt

fun findGitExecutableProblemHandler(project: Project): GitExecutableProblemHandler {
  return when {
    SystemInfo.isWindows -> WindowsExecutableProblemHandler(project)
    SystemInfo.isMac -> MacExecutableProblemHandler(project)
    else -> DefaultExecutableProblemHandler(project)
  }
}

interface GitExecutableProblemHandler {

  @CalledInAwt
  fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit)

  @CalledInAwt
  fun showError(exception: Throwable, errorNotifier: ErrorNotifier) {
    showError(exception, errorNotifier, {})
  }
}

interface ErrorNotifier {

  @CalledInAny
  fun showError(text: String, description: String? = null, fixOption: FixOption)

  @CalledInAny
  fun showError(text: String, fixOption: FixOption) {
    showError(text, null, fixOption)
  }

  @CalledInAny
  fun showError(text: String)

  @CalledInAwt
  fun executeTask(title: String, cancellable: Boolean, action: () -> Unit)

  @CalledInAny
  fun changeProgressTitle(text: String)

  @CalledInAny
  fun showMessage(text: String)

  @CalledInAny
  fun hideProgress()

  @CalledInAny
  fun resetGitExecutable() {
    GitVcsApplicationSettings.getInstance().setPathToGit(null)
    GitExecutableManager.getInstance().dropExecutableCache()
  }

  sealed class FixOption(val text: String, @CalledInAwt val fix: () -> Unit) {
    class Standard(text: String, @CalledInAwt fix: () -> Unit) : FixOption(text, fix)

    // todo probably change to "Select on disk" instead of opening Preferences
    internal class Configure(val project: Project) : FixOption("Configure...", {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, GitVcs.NAME)
    })
  }
}

@CalledInAwt
internal fun showUnsupportedVersionError(project: Project, version: GitVersion, errorNotifier: ErrorNotifier) {
  errorNotifier.showError(unsupportedVersionMessage(version), unsupportedVersionDescription(), getLinkToConfigure(project))
}

internal fun unsupportedVersionMessage(version: GitVersion): String =
  GitBundle.message("git.executable.validation.error.version.title", version.presentation)

internal fun unsupportedVersionDescription(): String =
  GitBundle.message("git.executable.validation.error.version.message", GitVersion.MIN.presentation)

internal fun getLinkToConfigure(project: Project): ErrorNotifier.FixOption = ErrorNotifier.FixOption.Configure(project)

internal fun ProcessOutput.dumpToString() = "output: ${stdout}, error output: ${stderr}"

internal fun getErrorTitle(text: String, description: String?) =
  if (description == null) GitBundle.getString("git.executable.validation.error.start.title") else text

internal fun getErrorMessage(text: String, description: String?) = description ?: text


private class DefaultExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {
  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError(getPrettyErrorMessage(exception), getLinkToConfigure(project))
  }
}
