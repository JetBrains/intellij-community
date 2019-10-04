// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface TerminalShellCommandHandler {
  fun execute(project: Project, command: String): Boolean

  companion object {
    @JvmStatic
    val EP = ExtensionPointName.create<TerminalShellCommandHandler>("com.intellij.terminal.shellCommandHandler")
  }
}