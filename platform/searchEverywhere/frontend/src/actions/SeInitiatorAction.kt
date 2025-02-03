// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.searchEverywhere.frontend.SeFrontendService
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeInitiatorAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = SeFrontendService.isEnabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val service = SeFrontendService.getInstance(project)
    service.coroutineScope.launch {
      service.showPopup(null, e.dataContext)
    }
  }
}