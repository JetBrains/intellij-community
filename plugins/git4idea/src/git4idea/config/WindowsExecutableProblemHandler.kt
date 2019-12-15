// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import java.io.File

internal class WindowsExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {

  companion object {
    val LOG = logger<WindowsExecutableProblemHandler>()
  }

  private val gitexe = if (SystemInfo.is64Bit) "Git-2.24.1.2-64-bit.exe" else "Git-2.24.1.2-32-bit.exe"
  private val gitFile = File(PathManager.getTempPath(), gitexe)

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier) {
    errorNotifier.showError("Git is not installed", ErrorNotifier.FixOption.Standard("Download and install") {
      errorNotifier.executeTask("Downloading...", false) {
        // todo display determinate inline progress for downloading
        if (downloadGit(errorNotifier)) {
          errorNotifier.changeProgressTitle("Installing...")
          installGit(errorNotifier)
        }
      }
    })
  }

  private fun downloadGit(errorNotifier: ErrorNotifier): Boolean {
    // todo get the JSON with the URL from our server, then get the URL from the JSON
    val url = "https://github.com/git-for-windows/git/releases/download/v2.24.1.windows.2/$gitexe"
    try {
      HttpRequests.request(url).saveToFile(gitFile, ProgressManager.getInstance().progressIndicator)
      return true
    }
    catch (e: Exception) {
      LOG.warn("Couldn't download $gitexe from $url")
      // todo special text for the network unavailable error
      errorNotifier.showError("Couldn't download Git, please do it manually", getLinkToConfigure(project))
      return false
    }
  }

  private fun installGit(errorNotifier: ErrorNotifier) {
    val commandLine = GeneralCommandLine()
      .withExePath(gitFile.path)
      .withParameters("/verysilent")
    try {
      val output = ExecUtil.execAndGetOutput(commandLine)

      if (!output.checkSuccess(LOG)) {
        errorNotifier.showError("Couldn't install Git, please do it manually", getLinkToConfigure(project))
      }
      else {
        LOG.info("Installed Git. ${output.dumpToString()}")
        errorNotifier.showMessage("Git has been installed")
      }
    }
    catch (e: Exception) {
      LOG.warn("Couldn't run $commandLine")
      errorNotifier.showError("Couldn't install Git, please do it manually", getLinkToConfigure(project))
    }
  }
}