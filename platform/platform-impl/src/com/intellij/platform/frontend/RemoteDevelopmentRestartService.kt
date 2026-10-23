// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.frontend

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Restarts selected sides of a remote development session.
 * The caller must obtain confirmation before calling this service on EDT.
 */
@ApiStatus.Internal
interface RemoteDevelopmentRestartService {
  /** A backend restart uses the standard reconnection flow. When both flags are false, this method does nothing. */
  suspend fun restart(project: Project, clientNeedsRestart: Boolean, backendNeedsRestart: Boolean)
}
