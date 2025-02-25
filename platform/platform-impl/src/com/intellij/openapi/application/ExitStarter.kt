// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.impl.LaterInvocator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private const val ourRestartParameter = "--restart"

@ApiStatus.Internal
class ExitStarter private constructor() : ApplicationStarterBase(0, 1, 2) {
  override val usageMessage: String
    get() = IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.exit")

  override val isHeadless: Boolean
    get() = true

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    forceExitApplication("ExitStarter requested exit", args.any { ourRestartParameter == it })
    return CliResult.OK
  }

  companion object {
    fun forceExitApplication(reason: @NonNls String, restart: Boolean = false) {
      val application = ApplicationManager.getApplication()
      // We need to invoke the method in Modality.any() to execute the method even (especially) if the modality stack is not empty
      // The method pops elements from modality stack until it's empty
      application.invokeLater({ LaterInvocator.forceLeaveAllModals(reason) }, ModalityState.any())
      application.invokeLater({ application.exit(true, true, restart) }, ModalityState.nonModal())
    }
  }
}