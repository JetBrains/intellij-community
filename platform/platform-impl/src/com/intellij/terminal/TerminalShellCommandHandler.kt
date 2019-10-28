// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.openapi.application.Experiments
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface TerminalShellCommandHandler {
  /**
   * Returns true if handler allows to launch the {@param #command} in a smart way.
   * E.g. open a particular UI in IDE and use parameters fetched from the {@param #command}
   */
  fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean

  /**
   * Launches matched command, see {@see #matches}.
   * Returns true if command has been successfully executed, false if failed.
   */
  fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean

  companion object {
    private val LOG = Logger.getInstance(TerminalShellCommandHandler::class.java)
    private val EP = ExtensionPointName.create<TerminalShellCommandHandler>("com.intellij.terminal.shellCommandHandler")

    fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
      if (!Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) return false

      return EP.extensionList.any { it.matches(project, workingDirectory, localSession, command) }
    }

    fun executeShellCommandHandler(project: Project, workingDirectory: String?, localSession: Boolean, command: String) {
      if (!Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) return

      EP.extensionList
        .find { it.matches(project, workingDirectory, localSession, command) }
        ?.execute(project, workingDirectory, localSession, command)
      ?: LOG.warn("Executing non matched command: $command")
    }
  }
}