// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Opens a welcome-screen tab alongside regular editor tabs during startup.
 *
 * Implementations run earlier than [com.intellij.openapi.startup.ProjectActivity], which is too
 * late for this initialization stage.
 */
@Internal
interface WelcomeScreenTabService {
  /**
   * Opens a welcome tab for the current project when applicable.
   */
  suspend fun openTab()

  companion object {
    /**
     * Returns the project-level implementation.
     */
    fun getInstance(project: Project): WelcomeScreenTabService = project.getService(WelcomeScreenTabService::class.java)
  }
}

/**
 * Default no-op implementation used when no product-specific implementation is registered.
 */
internal class NoWelcomeScreenTabService : WelcomeScreenTabService {
  override suspend fun openTab() = Unit
}

