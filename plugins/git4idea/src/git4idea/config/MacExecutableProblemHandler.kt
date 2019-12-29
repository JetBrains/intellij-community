// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import git4idea.config.GitExecutableProblemsNotifier.getPrettyErrorMessage
import git4idea.i18n.GitBundle

class MacExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {
  companion object {
    val LOG = logger<MacExecutableProblemHandler>()
  }

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier) {
    when {
      isXcodeLicenseError(exception) -> showXCodeLicenseError(errorNotifier)
      isInvalidActiveDeveloperPath(exception) -> showInvalidActiveDeveloperPathError(errorNotifier)
      else -> showGenericError(exception, errorNotifier)
    }
  }

  private fun showGenericError(exception: Throwable, errorNotifier: ErrorNotifier) {
    errorNotifier.showError(getPrettyErrorMessage(exception), ErrorNotifier.FixOption.Standard("Install") {
      errorNotifier.executeTask("Starting system installer", false) {
        execXCodeSelectInstall(errorNotifier)
      }
    })
  }

  private fun showCouldntStartInstallerError(errorNotifier: ErrorNotifier) {
    errorNotifier.showError("Couldn't Install Command Line Tools", getLinkToConfigure(project))
  }

  private fun showXCodeLicenseError(errorNotifier: ErrorNotifier) {
    errorNotifier.showError(GitBundle.getString("git.executable.validation.error.xcode.title"),
                            GitBundle.getString("git.executable.validation.error.xcode.message"),
                            getLinkToConfigure(project))
  }

  private fun showInvalidActiveDeveloperPathError(errorNotifier: ErrorNotifier) {
    val fixPathOption = ErrorNotifier.FixOption.Standard("Fix Path") {
      errorNotifier.executeTask("Requesting XCode Command Line Developer Tools" + StringUtil.ELLIPSIS, false) {
        execXCodeSelectInstall(errorNotifier)
      }
    }
    errorNotifier.showError("Invalid path to Command Line Tools", fixPathOption)
  }

  /**
   * Check if validation failed because the XCode license was not accepted yet
   */
  private fun isXcodeLicenseError(exception: Throwable): Boolean =
    isXcodeError(exception) { it.contains("Agreeing to the Xcode/iOS license") }

  /**
   * Check if validation failed because the XCode command line tools were not found
   */
  private fun isInvalidActiveDeveloperPath(exception: Throwable): Boolean =
    isXcodeError(exception) { it.contains("invalid active developer path") && it.contains("xcrun") }

  private fun isXcodeError(exception: Throwable, messageIndicator: (String) -> Boolean): Boolean {
    val message = if (exception is GitVersionIdentificationException) {
      exception.cause?.message
    }
    else {
      exception.message
    }
    return (message != null && messageIndicator(message))
  }

  private fun execXCodeSelectInstall(errorNotifier: ErrorNotifier) {
    try {
      val cmd = GeneralCommandLine("xcode-select", "--install")
      val output = ExecUtil.execAndGetOutput(cmd)
      if (!output.checkSuccess(LOG)) {
        LOG.warn(output.stderr)
        showCouldntStartInstallerError(errorNotifier)
      }
      errorNotifier.hideProgress()
    }
    catch (e: Exception) {
      LOG.warn(e)
      showCouldntStartInstallerError(errorNotifier)
    }
  }
}