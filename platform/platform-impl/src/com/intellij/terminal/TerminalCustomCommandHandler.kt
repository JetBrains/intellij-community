// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface TerminalCustomCommandHandler {
  fun isAcceptable(command: String): Boolean

  fun execute(project: Project, command: String): Boolean

  companion object {
    val EP = ExtensionPointName.create<TerminalCustomCommandHandler>("com.intellij.terminal.commandHandler");

    fun findCustomCommandHandler(command: String): TerminalCustomCommandHandler? {
      return EP.extensionList.find { it.isAcceptable(command) }
    }
  }
}