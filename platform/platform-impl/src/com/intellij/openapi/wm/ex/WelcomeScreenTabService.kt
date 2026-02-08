// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus

/**
 * Service that allows to open welcome screen tab simultaneously with the other tabs.
 *
 * ProjectActivity is executed too late for that purpose.
 */
@ApiStatus.Internal
interface WelcomeScreenTabService {
  suspend fun openTab()
  suspend fun openProjectView(toolWindowManager: ToolWindowManager)
  fun getProjectPaneToActivate(): String?

  companion object {
    fun getInstance(project: Project): WelcomeScreenTabService = project.getService(WelcomeScreenTabService::class.java)
  }
}

internal class NoWelcomeScreenTabService : WelcomeScreenTabService {
  override suspend fun openTab() = Unit
  override suspend fun openProjectView(toolWindowManager: ToolWindowManager) = Unit
  override fun getProjectPaneToActivate(): String? = null
}
