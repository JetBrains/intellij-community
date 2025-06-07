// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.components.service
import org.jetbrains.plugins.gitlab.GitLabServersManager

/**
 * Manages the creation of [GitLabApi] clients.
 */
abstract class GitLabApiManager {
  protected abstract val serversManager: GitLabServersManager

  /**
   * Gets a client for the given token. The created client can make authenticated
   * requests to a GitLab server of choice. Because a token is passed when constructing
   * the API client, the client can usually only be used for a single intended server
   * until the expiration of that token.
   *
   * For a more robust client, use the overloaded version with a token supplier.
   */
  fun getClient(server: GitLabServerPath,
                token: String): GitLabApi =
    getClient(server) { token }

  /**
   * Gets a client that fetches tokens using the given supplier. The created client
   * can make authenticated requests to a GitLab server of choice. Because a token
   * supplier is passed when constructing the API client, the client is usually
   * intended to only be used for a single intended server. New tokens could be
   * supplied by the token supplier, however.
   */
  fun getClient(server: GitLabServerPath,
                tokenSupplier: () -> String): GitLabApi =
    GitLabApiImpl(serversManager, server, tokenSupplier)

  /**
   * Gets an unauthenticated API Client that can be used for requests that are sure
   * to need no authentication. For any other requests, please use [getClient].
   */
  fun getUnauthenticatedClient(server: GitLabServerPath): GitLabApi =
    GitLabApiImpl(serversManager, server)
}

class GitLabApiManagerImpl : GitLabApiManager() {
  override val serversManager: GitLabServersManager by lazy { service<GitLabServersManager>() }
}
