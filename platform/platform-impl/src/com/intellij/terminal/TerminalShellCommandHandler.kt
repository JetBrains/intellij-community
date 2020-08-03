// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls

interface TerminalShellCommandHandler {
  /**
   * Returns true if handler allows to launch the {@param #command} in a smart way.
   * E.g. open a particular UI in IDE and use parameters fetched from the {@param #command}
   */
  fun matches(project: Project, workingDirectory: String?, localSession: Boolean, @NonNls command: String): Boolean

  /**
   * Launches matched command, see {@see #matches}.
   * Returns true if command has been successfully executed, false if failed.
   */
  fun execute(project: Project, workingDirectory: String?, localSession: Boolean, @NonNls command: String, executorAction: TerminalExecutorAction): Boolean

  companion object {
    private val LOG = Logger.getInstance(TerminalShellCommandHandler::class.java)
    val EP = ExtensionPointName.create<TerminalShellCommandHandler>("com.intellij.terminal.shellCommandHandler")

    fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
      return EP.extensionList.any { it.matches(project, workingDirectory, localSession, command) }
    }

    fun executeShellCommandHandler(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executorAction: TerminalExecutorAction) {
      EP.extensionList
        .find { it.matches(project, workingDirectory, localSession, command) }
        ?.execute(project, workingDirectory, localSession, command, executorAction)
      ?: LOG.warn("Executing non matched command: $command")
    }
  }
}