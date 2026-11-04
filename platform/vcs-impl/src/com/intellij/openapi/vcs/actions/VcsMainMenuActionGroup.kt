// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder

internal class VcsMainMenuActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val vcs = VcsMappingsHolder.getInstance(project).getSingleActiveVcs() ?: return
    val presentation = findVcsPresentation(project, vcs) ?: return
    e.presentation.isEnabledAndVisible = !presentation.isWithCustomMenu
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
