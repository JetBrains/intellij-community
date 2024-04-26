// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SudoCommandProvider {
  companion object {
    val EXTENSION_POINT_NAME: ExtensionPointName<SudoCommandProvider> = create("com.intellij.sudoCommandProvider")
  }

  /**
   * Executes the given command with elevated privileges.
   *
   * @param wrappedCommand The command to be executed with elevated privileges.
   * @return The [GeneralCommandLine] object representing the sudo command,
   * or `null` if no suitable utils for privilege elevation were found.
   * @see com.intellij.execution.util.ExecUtil.sudoCommand
   */
  fun sudoCommand(wrappedCommand: GeneralCommandLine): GeneralCommandLine?
}