// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

/**
 * Defines operation to be used to check incoming commits on remote.
 */
enum class GitIncomingRemoteCheckStrategy {
  FETCH,
  LS_REMOTE,
  NONE;
}
