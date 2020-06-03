// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.CommonBundle
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import git4idea.GitVcs
import git4idea.config.GitExecutableProblemsNotifier.getPrettyErrorMessage
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence
import org.jetbrains.annotations.Nls.Capitalization.Title

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
  fun showError(@Nls(capitalization = Sentence) text: String,
                @Nls(capitalization = Sentence) description: String? = null,
                fixOption: FixOption?)

  @CalledInAny
  fun showError(@Nls(capitalization = Sentence) text: String, fixOption: FixOption?) {
    showError(text, null, fixOption)
  }

  @CalledInAny
  fun showError(@Nls(capitalization = Sentence) text: String)

  @CalledInAwt
  fun executeTask(@Nls(capitalization = Title) title: String, cancellable: Boolean, action: () -> Unit)

  @CalledInAny
  fun changeProgressTitle(@Nls(capitalization = Title) text: String)

  @CalledInAny
  fun showMessage(@Nls(capitalization = Sentence) text: String)

  @CalledInAny
  fun hideProgress()

  @CalledInBackground
  fun resetGitExecutable() {
    GitVcsApplicationSettings.getInstance().setPathToGit(null)
    GitExecutableManager.getInstance().dropExecutableCache()
  }

  sealed class FixOption(@Nls(capitalization = Title) val text: String, @CalledInAwt val fix: () -> Unit) {
    class Standard(@Nls(capitalization = Title) text: String, @CalledInAwt fix: () -> Unit) : FixOption(text, fix)

    // todo probably change to "Select on disk" instead of opening Preferences
    internal class Configure(val project: Project) : FixOption(CommonBundle.message("action.text.configure.ellipsis"), {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, GitVcs.NAME)
    })
  }
}

@CalledInAwt
internal fun showUnsupportedVersionError(project: Project, version: GitVersion, errorNotifier: ErrorNotifier) {
  val description = if (version.type == GitVersion.Type.WSL1) unsupportedWslVersionDescription() else unsupportedVersionDescription()
  errorNotifier.showError(unsupportedVersionMessage(version), description, getLinkToConfigure(project))
}

internal fun unsupportedVersionMessage(version: GitVersion): String =
  GitBundle.message("git.executable.validation.error.version.title", version.presentation)

internal fun unsupportedVersionDescription(): String =
  GitBundle.message("git.executable.validation.error.version.message", GitVersion.MIN.presentation)

internal fun unsupportedWslVersionDescription(): String =
  GitBundle.message("git.executable.validation.error.wsl1.unsupported.message")

internal fun getLinkToConfigure(project: Project): ErrorNotifier.FixOption = ErrorNotifier.FixOption.Configure(project)

internal fun ProcessOutput.dumpToString() = "output: ${stdout}, error output: ${stderr}"

internal fun getErrorTitle(text: String, description: String?) =
  if (description == null) GitBundle.getString("git.executable.validation.error.start.title") else text

internal fun getErrorMessage(text: String, description: String?) = description ?: text

internal fun getHumanReadableErrorFor(exception: Throwable): String? {
  if (exception is GitNotInstalledException) {
    return null
  }
  return getPrettyErrorMessage(exception)
}

private class DefaultExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {
  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError(GitBundle.message("executable.error.git.not.installed"),
                            getHumanReadableErrorFor(exception),
                            getLinkToConfigure(project))
  }
}
