// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.SystemInfo.isJetBrainsJvm
import com.intellij.openapi.util.SystemInfo.isMac

internal class EnableMetalRenderingAction: DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isMac && isJetBrainsJvm && VMOptions.canWriteOptions()
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    System.getProperty("sun.java2d.metal", "true").toBoolean()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    VMOptions.setProperty("sun.java2d.metal", if (state) null else "false")
    RegistryBooleanOptionDescriptor.suggestRestart(null)
  }
}
