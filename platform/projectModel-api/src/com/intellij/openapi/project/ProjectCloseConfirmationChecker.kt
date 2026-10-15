// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import org.jetbrains.annotations.ApiStatus

/**
 * A [ProjectManagerListener] can implement this interface to tell if the project close needs a user confirmation.
 *
 * The platform calls it before it reuses the frame of the Welcome project.
 * When a checker returns `true`, the platform asks the user where to open the new project.
 */
@ApiStatus.Internal
interface ProjectCloseConfirmationChecker {
  /**
   * Returns `true` if the close of [project] needs a user confirmation.
   * The implementation must not show a UI.
   */
  suspend fun needsConfirmationToClose(project: Project): Boolean
}
