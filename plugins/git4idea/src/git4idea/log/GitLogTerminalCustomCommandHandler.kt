// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class GitLogTerminalCustomCommandHandler : TerminalShellCommandHandler {
  private val LOG = Logger.getInstance(GitLogTerminalCustomCommandHandler::class.java)
  override fun execute(project: Project, getWorkingDirectory: () -> String?, command: String): Boolean {
    if (!command.startsWith("git log")) {
      return false
    }

    val workingDirectory = getWorkingDirectory.invoke()

    if (workingDirectory == null) {
      LOG.warn("Cannot open git log for unknown root.")
      return false
    }

    val path = VcsContextFactory.SERVICE.getInstance().createFilePath(workingDirectory, true)
    val rootObject = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path) ?: return false
    if (VcsLogManager.findLogProviders(listOf(rootObject), project).isEmpty()) return false

    VcsLogContentUtil.openMainLogAndExecute(project) {
      ui -> ui.filterUi.setFilter(VcsLogFilterObject.fromRoot(rootObject.path))
    }

    return true
  }
}