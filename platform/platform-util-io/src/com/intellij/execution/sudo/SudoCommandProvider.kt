// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.sudo

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface SudoCommandProvider {
  fun isAvailable(): Boolean

  /**
   * Executes the given command with elevated privileges.
   *
   * @param wrappedCommand The command to be executed with elevated privileges.
   * @return The [GeneralCommandLine] object representing the sudo command,
   * or `null` if no suitable utils for privilege elevation were found.
   * @see com.intellij.execution.util.ExecUtil.sudoCommand
   */
  fun sudoCommand(wrappedCommand: GeneralCommandLine, prompt: @Nls String): GeneralCommandLine?

  companion object {
    @JvmStatic
    fun getInstance(): SudoCommandProvider = ApplicationManager.getApplication().getService(SudoCommandProvider::class.java)
  }
}