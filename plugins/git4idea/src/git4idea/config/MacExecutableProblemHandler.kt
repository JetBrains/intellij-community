// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.NonNls
import java.io.File

class MacExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {
  companion object {
    val LOG = logger<MacExecutableProblemHandler>()

    private const val XCODE_LICENSE_ERROR: @NonNls String = "Agreeing to the Xcode/iOS license"
    private const val XCODE_DEVELOPER_PART_ERROR: @NonNls String = "invalid active developer path"
    private const val XCODE_XCRUN: @NonNls String = "xcrun"
  }

  private val tempPath = FileUtil.createTempDirectory("git-install", null)
  private val mountPoint = File(tempPath, "mount")

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    when {
      isXcodeLicenseError(exception) -> showXCodeLicenseError(errorNotifier)
      isInvalidActiveDeveloperPath(exception) -> showInvalidActiveDeveloperPathError(errorNotifier)
      else -> showGenericError(exception, errorNotifier, onErrorResolved)
    }
  }

  private fun showGenericError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError(GitBundle.message("executable.error.git.not.installed"),
      getHumanReadableErrorFor(exception),
      ErrorNotifier.FixOption.Standard(GitBundle.message("install.download.and.install.action")) {
        this.downloadAndInstall(errorNotifier, onErrorResolved)
      })
  }

  internal fun downloadAndInstall(errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.executeTask(GitBundle.message("install.downloading.progress"), false) {
      try {
        val installer = fetchInstaller(errorNotifier) { it.os == "macOS" && it.pkgFileName != null }
        if (installer != null) {
          val fileName = installer.fileName
          val dmgFile = File(tempPath, fileName)
          val pkgFileName = installer.pkgFileName!!
          if (downloadGit(installer, dmgFile, project, errorNotifier)) {
            errorNotifier.changeProgressTitle(GitBundle.message("install.installing.progress"))
           installGit(dmgFile, pkgFileName, errorNotifier, onErrorResolved)
          }
        }
      }
      finally {
        FileUtil.delete(tempPath)
      }
    }
  }

  private fun installGit(dmgFile: File, pkgFileName: String, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    if (attachVolume(dmgFile, errorNotifier)) {
      try {
        if (installPackageOrShowError(pkgFileName, errorNotifier)) {
          errorNotifier.showMessage(GitBundle.message("install.success.message"))
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
      val cmd = if (sudo) ExecUtil.sudoCommand(commandLine, GitBundle.message("title.sudo.command.install.git")) else commandLine
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
    errorNotifier.showError(GitBundle.message("install.general.error"), getLinkToConfigure(project))
  }

  private fun showCouldntStartInstallerError(errorNotifier: ErrorNotifier) {
    errorNotifier.showError(GitBundle.message("install.mac.error.couldnt.start.command.line.tools"), getLinkToConfigure(project))
  }

  private fun showXCodeLicenseError(errorNotifier: ErrorNotifier) {
    errorNotifier.showError(GitBundle.message("git.executable.validation.error.xcode.title"),
                            GitBundle.message("git.executable.validation.error.xcode.message"),
                            getLinkToConfigure(project))
  }

  private fun showInvalidActiveDeveloperPathError(errorNotifier: ErrorNotifier) {
    val fixPathOption = ErrorNotifier.FixOption.Standard(GitBundle.message("executable.mac.fix.path.action")) {
      errorNotifier.executeTask(GitBundle.message("install.mac.requesting.command.line.tools") + StringUtil.ELLIPSIS, false) {
        execXCodeSelectInstall(errorNotifier)
      }
    }
    errorNotifier.showError(GitBundle.message("executable.mac.error.invalid.path.to.command.line.tools"), fixPathOption)
  }

  /**
   * Check if validation failed because the XCode license was not accepted yet
   */
  private fun isXcodeLicenseError(exception: Throwable): Boolean =
    isXcodeError(exception) { it.contains(XCODE_LICENSE_ERROR) }

  /**
   * Check if validation failed because the XCode command line tools were not found
   */
  private fun isInvalidActiveDeveloperPath(exception: Throwable): Boolean =
    isXcodeError(exception) { it.contains(XCODE_DEVELOPER_PART_ERROR) && it.contains(XCODE_XCRUN) }

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
