// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.Consumer

class GitLogTerminalCustomCommandHandler : TerminalShellCommandHandler {
  override fun execute(project: Project, command: String): Boolean {
    if (command.startsWith("git log")) {
      VcsLogContentUtil.openMainLogAndExecute(project, com.intellij.util.EmptyConsumer.getInstance())
      return true
    }
    return false
  }
}