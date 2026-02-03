// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.auth.Account

/**
 * Represents a connection to a git repository hosting server
 *
 * @param M - repository
 * @param A - account
 */
interface HostedGitRepositoryConnection<M : HostedGitRepositoryMapping, A : Account> {
  val repo: M
  val account: A

  /**
   * Close connection and suspend until closed
   */
  suspend fun close()

  /**
   * Suspend until connection is closed
   */
  suspend fun awaitClose()
}
