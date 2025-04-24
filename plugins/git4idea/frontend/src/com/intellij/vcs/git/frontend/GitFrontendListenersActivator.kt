// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.frontend.widget.GitWidgetStateHolder
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled

internal class GitFrontendListenersActivator : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (Registry.isRdBranchWidgetEnabled()) {
      GitWidgetStateHolder.getInstance(project).initStateUpdate(selectedFile = null)
    }
  }
}