// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags

/** Internal action to toggle Jewel's custom popup renderer. */
@OptIn(ExperimentalJewelApi::class)
internal class JewelCustomPopupRendererAction : DumbAwareToggleAction(
) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = JewelFlags.useCustomPopupRenderer


  override fun setSelected(e: AnActionEvent, state: Boolean) {
    JewelFlags.useCustomPopupRenderer = state
  }
}