// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import git4idea.i18n.GitBundle
import java.io.File

class MacExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {
  companion object {
    val LOG = logger<MacExecutableProblemHandler>()
  }

  private val gitFileName = "git-2.23.0-intel-universal-mavericks.dmg"
  private val tempPath = FileUtil.createTempDirectory("git-install", null)
  private val mountPoint = File(tempPath, "mount")
  private val gitFile = File(tempPath, gitFileName)
  private val pkgFileName = "git-2.23.0-intel-universal-mavericks.pkg"

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    when {
      isXcodeLicenseError(exception) -> showXCodeLicenseError(errorNotifier)
      isInvalidActiveDeveloperPath(exception) -> showInvalidActiveDeveloperPathError(errorNotifier)
      else -> showGenericError(exception, errorNotifier, onErrorResolved)
    }
  }

  private fun showGenericError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError("Git is not installed", ErrorNotifier.FixOption.Standard("Download and install") {
      errorNotifier.executeTask("Downloading...", false) {
        val url = "https://netix.dl.sourceforge.net/project/git-osx-installer/$gitFileName"
        if (downloadGit(project, url, gitFile, errorNotifier)) {
          errorNotifier.changeProgressTitle("Installing...")
          installGit(errorNotifier, onErrorResolved)
        }
      }
    })
  }

  private fun installGit(errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    if (attachVolume(gitFile, errorNotifier)) {
      try {
        if (installPackageOrShowError(pkgFileName, errorNotifier)) {
          errorNotifier.showMessage("Git has been installed")
          onErrorResolved()
          errorNotifier.resetGitExecutable()
        }
      }
      finally {
        detachVolume()
      }
    }
  }

  private fun attachVolume(file: File, errorNotifier: ErrorNotifier): Boolean {
    val cmd = GeneralCommandLine("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse",
                                 "-mountpoint", mountPoint.path, file.path)
    return runOrShowError(cmd, errorNotifier, sudo = false)
  }

  private fun installPackageOrShowError(pkgFileName: String, errorNotifier: ErrorNotifier) =
    runOrShowError(GeneralCommandLine("installer", "-package", "${mountPoint}/$pkgFileName", "-target", "/"),
                   errorNotifier, sudo = true)

  private fun detachVolume() {
    runCommand(GeneralCommandLine("hdiutil", "detach", mountPoint.path), sudo = false, onError = {})
  }

  private fun runOrShowError(commandLine: GeneralCommandLine, errorNotifier: ErrorNotifier, sudo: Boolean): Boolean {
    return runCommand(commandLine, sudo) {
      showCouldntInstallError(errorNotifier)
    }
  }

  private fun runCommand(commandLine: GeneralCommandLine, sudo: Boolean, onError: () -> Unit): Boolean {
    try {
      val cmd = if (sudo) ExecUtil.sudoCommand(commandLine, "Install Git") else commandLine
      val output = ExecUtil.execAndGetOutput(cmd)
      if (output.checkSuccess(LOG)) {
        return true
      }
      LOG.warn(output.stderr)
      onError()
      return false
    }
    catch (e: Exception) {
      LOG.warn(e)
      onError()
      return false
    }
  }

  private fun showCouldntInstallError(errorNotifier: ErrorNotifier) {
    errorNotifier.showError("Couldn't Install Git", getLinkToConfigure(project))
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
      errorNotifier.hideProgress()
      if (!output.checkSuccess(LOG)) {
        LOG.warn(output.stderr)
        showCouldntStartInstallerError(errorNotifier)
      }
      else {
        errorNotifier.resetGitExecutable()
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
      showCouldntStartInstallerError(errorNotifier)
    }
  }
}