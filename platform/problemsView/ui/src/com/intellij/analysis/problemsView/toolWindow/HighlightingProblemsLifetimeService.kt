// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

@Service(Service.Level.PROJECT)
internal class HighlightingProblemsLifetimeService(private val cs: CoroutineScope) {

  fun createRootLifetime(root: ProblemsViewHighlightingFileRoot): ProblemLifetime {
    val rootScope = cs
      .childScope("highlighting root of file: ${root.file.name}")
      .apply { coroutineContext.job.cancelOnDispose(root) }

    return ProblemLifetime(rootScope)
  }

  companion object {
    fun getInstance(project: Project): HighlightingProblemsLifetimeService = project.service()
  }
}
