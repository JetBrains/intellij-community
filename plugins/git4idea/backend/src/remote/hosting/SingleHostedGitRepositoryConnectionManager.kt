// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.auth.ServerAccount
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Hold a single repository connection
 * If a new connection is requested the old one should be closed first
 */
interface SingleHostedGitRepositoryConnectionManager<
  M : HostedGitRepositoryMapping,
  A : ServerAccount,
  C : HostedGitRepositoryConnection<M, A>> {

  val connectionState: StateFlow<C?>

  /**
   * Connect to [repo] with [account] and save the connection
   */
  suspend fun openConnection(repo: M, account: A): C?

  /**
   * Close the current connection if any
   */
  suspend fun closeConnection()
}

class SingleHostedGitRepositoryConnectionManagerImpl<
  M : HostedGitRepositoryMapping,
  A : ServerAccount,
  C : HostedGitRepositoryConnection<M, A>>(
  parentCs: CoroutineScope,
  private val connectionFactory: HostedGitRepositoryConnectionFactory<M, A, C>
) : SingleHostedGitRepositoryConnectionManager<M, A, C> {

  private val cs = parentCs.childScope(Dispatchers.Default)

  private val _connectionState = MutableStateFlow<C?>(null)
  override val connectionState: StateFlow<C?> = _connectionState.asStateFlow()
  private val connectionStateGuard = Mutex()

  override suspend fun openConnection(repo: M, account: A): C? =
    withContext(cs.coroutineContext) {
      connectionStateGuard.withLock {
        val current = _connectionState.value
        if (current == null || current.repo != repo || current.account != account) {
          current?.close()
          _connectionState.value = connectionFactory.connect(cs, repo, account)
        }
        _connectionState.value
      }
    }

  override suspend fun closeConnection() =
    withContext(cs.coroutineContext) {
      connectionStateGuard.withLock {
        _connectionState.value?.close()
        _connectionState.value = null
      }
    }
}