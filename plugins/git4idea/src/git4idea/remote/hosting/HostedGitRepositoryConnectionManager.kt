// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.auth.ServerAccount
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles the state of git repository hosting server connection
 */
interface HostedGitRepositoryConnectionManager<M : HostedGitRepositoryMapping, A : ServerAccount, C : HostedGitRepositoryConnection<M, A>> {
  val state: StateFlow<C?>

  suspend fun tryConnect(repo: M, account: A)
  suspend fun disconnect()
}