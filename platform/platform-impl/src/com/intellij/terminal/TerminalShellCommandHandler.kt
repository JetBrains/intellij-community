// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.openapi.application.Experiments
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface TerminalShellCommandHandler {
  fun isAvailable(project: Project, command: String): Boolean

  fun execute(project: Project, getWorkingDirectory: () -> String?, command: String): Boolean

  companion object {
    private val LOG = Logger.getInstance(TerminalShellCommandHandler::class.java)
    @JvmStatic
    val EP = ExtensionPointName.create<TerminalShellCommandHandler>("com.intellij.terminal.shellCommandHandler")

    fun isAvailable(project: Project, command: String): Boolean {
      if (Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) {
        return EP.extensionList.any { it.isAvailable(project, command) }
      }
      return false
    }

    fun executeShellCommandHandler(project: Project, command: String, getWorkingDirectory: () -> String?) {
      if (Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) {
        EP.extensionList.find { it.isAvailable(project, command) }?.execute(project, getWorkingDirectory, command)
        ?: LOG.warn("Executing non matched command: $command")
      }
    }
  }
}