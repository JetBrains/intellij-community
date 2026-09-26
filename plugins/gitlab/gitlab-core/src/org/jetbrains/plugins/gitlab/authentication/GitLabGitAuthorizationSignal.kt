// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import kotlinx.coroutines.flow.Flow

/**
 * Emitter of the "try to authorize via git" signal
 */
internal interface GitLabGitAuthorizationSignal {
  /**
   * Emitted when the user requests to fall back to git-based authorization.
   */
  val tryGitAuthorizationSignal: Flow<Unit>

  fun tryGitAuthorization()
}
