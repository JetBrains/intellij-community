// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class ProjectErrorsLifetimeProvider(private val coroutineScope: CoroutineScope) {
  fun getLifetime(): ProblemLifetime {
    return ProblemLifetime(coroutineScope)
  }

  companion object {
    fun getInstance(project: Project): ProjectErrorsLifetimeProvider {
      return project.getService(ProjectErrorsLifetimeProvider::class.java)
    }
  }
}
