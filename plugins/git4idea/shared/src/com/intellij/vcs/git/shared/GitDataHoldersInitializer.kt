// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.vcs.git.shared.repo.GitRepositoriesFrontendHolder

internal class GitDataHoldersInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    GitRepositoriesFrontendHolder.getInstance(project).init()
  }
}
