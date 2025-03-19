// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.application
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.concurrent.Callable

internal class GradlePluginConsoleFilterProvider : ConsoleFilterProvider {

  override fun getDefaultFilters(project: Project): Array<out Filter?> {
    if (DumbService.isDumb(project)) return Filter.EMPTY_ARRAY

    val isPluginProject = runSafeReadAction(project) {
      PsiUtil.isPluginProject(project)
    }
    if (!isPluginProject) return Filter.EMPTY_ARRAY

    return arrayOf(PluginVerifierFilter(project))
  }

  private fun <T> runSafeReadAction(project: Project, action: () -> T): T {
    return if (application.isDispatchThread) {
      ReadAction.compute<T, Throwable>(action)
    } else {
      ReadAction
        .nonBlocking(Callable { action() })
        .expireWhen { project.isDisposed }
        .executeSynchronously()
    }
  }
}
