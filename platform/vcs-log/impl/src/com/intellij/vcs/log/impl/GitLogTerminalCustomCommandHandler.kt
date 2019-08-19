// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalCustomCommandHandler
import com.intellij.util.Consumer

class GitLogTerminalCustomCommandHandler : TerminalCustomCommandHandler {
  override fun isAcceptable(command: String): Boolean {
    return command.startsWith("git log")
  }

  override fun execute(project: Project, command: String): Boolean {
    ApplicationManager.getApplication().invokeLater { VcsLogContentUtil.openMainLogAndExecute(project, Consumer.EMPTY_CONSUMER) }
    return true
  }
}