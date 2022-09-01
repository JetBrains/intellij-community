// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.auth.AccountManager
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.DiscoveringAuthenticatingServersStateSupplier
import git4idea.remote.hosting.MappingHostedGitRepositoriesManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.GitHostingUrlUtil
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@Service
class GHHostedRepositoriesManager(project: Project) :
  MappingHostedGitRepositoriesManager<GithubServerPath, GHGitRepositoryMapping>(LOG, project, ServerDiscoverer(project)) {

  override fun createMapping(server: GithubServerPath, remote: GitRemoteUrlCoordinates) = GHGitRepositoryMapping.create(server, remote)

  private class ServerDiscoverer(project: Project)
    : DiscoveringAuthenticatingServersStateSupplier<GithubAccount, GithubServerPath>(project, GithubServerPath.DEFAULT_SERVER) {
    override fun accountManager(): AccountManager<GithubAccount, *> = service<GHAccountManager>()

    override fun getServer(account: GithubAccount): GithubServerPath = account.server

    override suspend fun checkForDedicatedServer(remote: GitRemoteUrlCoordinates): GithubServerPath? {
      val uri = GitHostingUrlUtil.getUriFromRemoteUrl(remote.url)
      LOG.debug("Extracted URI $uri from remote ${remote.url}")
      if (uri == null) return null

      val host = uri.host ?: return null
      val path = uri.path ?: return null
      val pathParts = path.removePrefix("/").split('/').takeIf { it.size >= 2 } ?: return null
      val serverSuffix = if (pathParts.size == 2) null else pathParts.subList(0, pathParts.size - 2).joinToString("/", "/")

      for (server in listOf(
        GithubServerPath(false, host, null, serverSuffix),
        GithubServerPath(true, host, null, serverSuffix),
        GithubServerPath(true, host, 8080, serverSuffix)
      )) {
        LOG.debug("Looking for GHE server at $server")
        try {
          service<GHEnterpriseServerMetadataLoader>().loadMetadata(server).await()
          LOG.debug("Found GHE server at $server")
          return server
        }
        catch (ignored: Throwable) {
        }
      }
      return null
    }
  }

  companion object {
    private val LOG = logger<GHHostedRepositoriesManager>()
  }
}