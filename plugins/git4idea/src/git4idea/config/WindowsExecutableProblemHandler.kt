// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File

internal class WindowsExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {

  companion object {
    val LOG = logger<WindowsExecutableProblemHandler>()
  }

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError("Git is not installed", ErrorNotifier.FixOption.Standard("Download and install") {
      errorNotifier.executeTask("Downloading...", true) {
        val installer = fetchInstaller(errorNotifier) { it.os == "windows" && archMatches(it.arch) }
        if (installer != null) {
          val fileName = installer.fileName
          val exeFile = File(PathManager.getTempPath(), fileName)
          if (downloadGit(installer, exeFile, project, errorNotifier)) {
            errorNotifier.changeProgressTitle("Installing...")
            installGit(exeFile, errorNotifier, onErrorResolved)
          }
        }
      }
    })
  }

  private fun archMatches(arch: String) = if (SystemInfo.is32Bit) arch == "x86_32" else arch == "x86_64"

  private fun installGit(exeFile: File, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    val commandLine = GeneralCommandLine()
      .withExePath(exeFile.path)
      .withParameters("/verysilent")
    try {
      val output = ExecUtil.execAndGetOutput(commandLine)

      if (!output.checkSuccess(LOG)) {
        errorNotifier.showError("Couldn't install Git, please do it manually", getLinkToConfigure(project))
      }
      else {
        LOG.info("Installed Git. ${output.dumpToString()}")
        errorNotifier.showMessage("Git has been installed")
        errorNotifier.resetGitExecutable()
        onErrorResolved()
      }
    }
    catch (e: Exception) {
      LOG.warn("Couldn't run $commandLine")
      errorNotifier.showError("Couldn't install Git, please do it manually", getLinkToConfigure(project))
    }
  }
}