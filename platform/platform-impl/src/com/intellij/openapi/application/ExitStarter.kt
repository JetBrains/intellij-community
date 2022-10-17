// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.impl.LaterInvocator

private const val ourRestartParameter = "--restart"

internal class ExitStarter private constructor() : ApplicationStarterBase(0, 1, 2) {
  override val commandName: String
    get() = "exit"

  override val usageMessage: String
    get() = IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.exit")

  override val isHeadless: Boolean
    get() = true

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    val application = ApplicationManager.getApplication()
    LaterInvocator.forceLeaveAllModals()
    application.invokeLater({ application.exit(true, true, args.any { ourRestartParameter == it }) }, ModalityState.NON_MODAL)
    return CliResult.OK
  }
}