// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.system.CpuArch
import git4idea.i18n.GitBundle
import java.io.File

internal class WindowsExecutableProblemHandler(val project: Project) : GitExecutableProblemHandler {

  companion object {
    val LOG = logger<WindowsExecutableProblemHandler>()
  }

  override fun showError(exception: Throwable, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    errorNotifier.showError(GitBundle.message("executable.error.git.not.installed"),
                            getHumanReadableErrorFor(exception),
                            ErrorNotifier.FixOption.Standard(GitBundle.message("install.download.and.install.action")) {
        errorNotifier.executeTask(GitBundle.message("install.downloading.progress"), true) {
          val installer = fetchInstaller(errorNotifier) { it.os == "windows" && archMatches(it.arch) }
          if (installer != null) {
            val fileName = installer.fileName
            val exeFile = File(PathManager.getTempPath(), fileName)
            try {
              if (downloadGit(installer, exeFile, project, errorNotifier)) {
                errorNotifier.changeProgressTitle(GitBundle.message("install.installing.progress"))
                installGit(exeFile, errorNotifier, onErrorResolved)
              }
            }
            finally {
              FileUtil.delete(exeFile)
            }
          }
        }
      })
  }

  private fun archMatches(arch: String) = when (CpuArch.CURRENT) {
    CpuArch.X86 -> arch == "x86_32"
    CpuArch.X86_64 -> arch == "x86_64"
    else -> false
  }

  private fun installGit(exeFile: File, errorNotifier: ErrorNotifier, onErrorResolved: () -> Unit) {
    val commandLine = GeneralCommandLine()
      .withExePath(exeFile.path)
      .withParameters("/verysilent")
    try {
      val output = ExecUtil.execAndGetOutput(commandLine)

      if (!output.checkSuccess(LOG)) {
        errorNotifier.showError(GitBundle.message("install.general.error"), getLinkToConfigure(project))
      }
      else {
        LOG.info("Installed Git. ${output.dumpToString()}")
        errorNotifier.showMessage(GitBundle.message("install.success.message"))
        errorNotifier.resetGitExecutable()
        onErrorResolved()
      }
    }
    catch (e: Exception) {
      LOG.warn("Couldn't run $commandLine")
      errorNotifier.showError(GitBundle.message("install.general.error"), getLinkToConfigure(project))
    }
  }
}
