// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.widget.GitToolbarWidgetActionBase

internal class GitToolbarWidgetAction: GitToolbarWidgetActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun doUpdate(e: AnActionEvent, project: Project) {
    if (!Registry.isRdBranchWidgetEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.doUpdate(e, project)
  }
}