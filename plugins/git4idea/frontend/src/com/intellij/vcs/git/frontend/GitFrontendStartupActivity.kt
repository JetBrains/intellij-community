// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.vcs.git.frontend.widget.GitWidgetStateHolder

internal class GitFrontendStartupActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    GitWidgetStateHolder.getInstance(project)
  }
}