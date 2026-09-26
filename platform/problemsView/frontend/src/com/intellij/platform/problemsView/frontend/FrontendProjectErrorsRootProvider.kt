// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.Root
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemsCollectorProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

@Service(Service.Level.PROJECT)
internal class FrontendProjectErrorsRootProvider(private val project: Project) : ProblemsCollectorProvider {

  private var root: FrontendProblemsViewProjectErrorsRoot? = null

  fun createRoot(panel: ProblemsViewPanel): FrontendProblemsViewProjectErrorsRoot {
    val newRoot = FrontendProblemsViewProjectErrorsRoot(panel, project)
    Disposer.register(panel, newRoot)
    root = newRoot
    return newRoot
  }

  override fun getProblemsCollector(): Root? {
    return root
  }

  companion object {
    fun getInstance(project: Project): FrontendProjectErrorsRootProvider =
      ProblemsCollectorProvider.getInstance(project) as FrontendProjectErrorsRootProvider
  }
}

