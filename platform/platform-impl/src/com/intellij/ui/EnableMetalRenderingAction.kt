// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo.isJetBrainsJvm
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.util.lang.JavaVersion

/**
 * @author Konstantin Bulenkov
 */
class EnableMetalRenderingAction: ToggleAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isMac && isJetBrainsJvm
                                         && JavaVersion.current().isAtLeast(17)
                                         && VMOptions.canWriteOptions()
  }

  override fun isSelected(e: AnActionEvent): Boolean = java.lang.Boolean.getBoolean("sun.java2d.metal")

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ApplicationManager.getApplication().runWriteAction {
      val path = VMOptions.getUserOptionsFile()
      val vmOptions = path?.toFile()?.readLines()?.filter { !it.contains("sun.java2d.metal") }?.toList()
      if (vmOptions != null) {
        val newVmOptions = if (state) ArrayList(vmOptions).also { it[0] = "-Dsun.java2d.metal=true" }
                           else ArrayList(vmOptions).also { it[0] = "-Dsun.java2d.metal=false" }
        path.toFile().writeText(newVmOptions.joinToString("\n"))
      }

      RegistryBooleanOptionDescriptor.suggestRestart(null)
    }
  }
}