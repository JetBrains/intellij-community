// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win

import com.intellij.ide.CliResult
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.project.ex.ProjectManagerEx
import java.nio.file.Path

internal class RecentProjectApplication : ApplicationStarterBase(1) {
  override val commandName: String
    get() = "reopen"

  override val usageMessage: String
    get() = "This command is used for internal purpose only." //NON-NLS

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    ProjectManagerEx.getInstanceEx().openProjectAsync(Path.of(args[1]).toAbsolutePath().normalize(), OpenProjectTask())
    return CliResult.OK
  }
}