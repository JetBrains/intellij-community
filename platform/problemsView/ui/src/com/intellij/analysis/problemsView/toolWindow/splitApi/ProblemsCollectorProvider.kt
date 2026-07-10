// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.analysis.problemsView.toolWindow.Root
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface ProblemsCollectorProvider {
  fun getProblemsCollector(): Root?

  companion object {
    fun getInstance(project: Project): ProblemsCollectorProvider =
      project.getService(ProblemsCollectorProvider::class.java)
  }
}
