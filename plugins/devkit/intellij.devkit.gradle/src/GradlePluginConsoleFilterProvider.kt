// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.concurrent.Callable

internal class GradlePluginConsoleFilterProvider : ConsoleFilterProvider {

  override fun getDefaultFilters(project: Project): Array<out Filter?> {
    if (DumbService.isDumb(project)) return Filter.EMPTY_ARRAY

    val isPluginProject = ReadAction
      .nonBlocking(Callable { PsiUtil.isPluginProject(project) })
      .expireWhen { project.isDisposed }
      .executeSynchronously()
    if (!isPluginProject) return Filter.EMPTY_ARRAY

    return arrayOf(PluginVerifierFilter(project))
  }
}

