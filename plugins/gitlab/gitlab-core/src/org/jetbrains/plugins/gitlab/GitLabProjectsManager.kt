// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.api.findServerForAlias
import com.intellij.collaboration.api.parseServerHostAliases
import com.intellij.collaboration.api.serverHostAliasesFlow
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitHostingUrlUtil
import git4idea.remote.hosting.GitHostingUrlUtil.getUriFromRemoteUrl
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.discoverServers
import git4idea.remote.hosting.gitRemotesFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabOAuthSettings
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.net.URI

interface GitLabProjectsManager : HostedGitRepositoriesManager<GitLabProjectMapping> {
  /**
   * Checks pre-configured servers from settings or does some guess to whether the given server path hosts a GitLab server.
   */
  suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean
}

internal class GitLabProjectsManagerImpl(private val project: Project, cs: CoroutineScope) : GitLabProjectsManager {

  override val knownRepositoriesState: StateFlow<Set<GitLabProjectMapping>> by lazy {
    val gitRemotesFlow = gitRemotesFlow(project).distinctUntilChanged()
    val aliasesFlow = serverHostAliasesFlow(GITLAB_ALIASES_SETTING_ID, ::parseGitLabHostAliases).distinctUntilChanged()

    val accountsServersFlow = service<GitLabAccountManager>().accountsState.map { accounts ->
      mutableSetOf(GitLabServerPath.DEFAULT_SERVER) + accounts.map { it.server }
    }.distinctUntilChanged()

    val notAliasedRemotes = gitRemotesFlow.combine(aliasesFlow) { remotes, aliases ->
      remotes.filter { remote -> remote.host?.let { it !in aliases } == true }
    }

    val discoveredServersFlow = notAliasedRemotes.discoverServers(accountsServersFlow) { remote ->
      GitHostingUrlUtil.findServerAt(LOG, remote) {
        val server = GitLabServerPath(it.toString())
        if (checkIsGitLabServer(server)) server else null
      }
    }.runningFold(emptySet<GitLabServerPath>()) { accumulator, value ->
      accumulator + value
    }.distinctUntilChanged()

    val serversFlow = accountsServersFlow.combine(discoveredServersFlow) { servers1, servers2 ->
      servers1 + servers2
    }

    val knownRepositoriesFlow = combine(
      gitRemotesFlow, serversFlow, accountsServersFlow, aliasesFlow
    ) { remotes, servers, accountServers, aliases ->
      remotes.mapNotNullTo(mutableSetOf()) { remote ->
        val host = remote.host ?: return@mapNotNullTo null
        val server = aliases[host]?.let { accountServers.findServerForAlias(it) }
                     ?: servers.find { GitHostingUrlUtil.match(it.toURI(), remote.url) }
        server?.let { GitLabProjectMapping.create(it, remote) }
      }
    }.onEach {
      LOG.debug("New list of known repos: $it")
    }

    knownRepositoriesFlow.stateIn(cs, SharingStarted.Eagerly, emptySet())
  }

  private val GitRemoteUrlCoordinates.host: String?
    get() = getUriFromRemoteUrl(url)?.host?.lowercase()

  override suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean =
    isPreConfigured(server) || service<GitLabServersManager>().checkIsGitLabServer(server)

  private fun isPreConfigured(server: GitLabServerPath): Boolean {
    val serverUri = server.toURI()
    return GitLabOAuthSettings.getInstance(project).state.customServers.any {
      val configuredUri = runCatching { GitLabServerPath(it.serverUri).toURI() }.getOrNull() ?: return@any false
      URIUtil.equalWithoutSchema(configuredUri, serverUri)
    }
  }

  companion object {
    private val LOG = logger<GitLabProjectsManager>()

    private const val GITLAB_ALIASES_SETTING_ID = "gitlab.aliases"
  }
}

/**
 * Parses the value of the `gitlab.aliases` setting into a map from an alias host to the GitLab server.
 *
 * The value is a comma-separated list of `<alias>=<server>` and `<alias>` entries.
 * The server is a URL, such as `https://git.example.com:8443/gitlab`, or a host that gets the `https` scheme.
 * An entry without a server maps the alias to [GitLabServerPath.DEFAULT_SERVER].
 * The function ignores an entry with an invalid server.
 */
internal fun parseGitLabHostAliases(value: String): Map<String, GitLabServerPath> =
  parseServerHostAliases(value, GitLabServerPath.DEFAULT_SERVER, ::parseAliasServer)

private fun parseAliasServer(value: String): GitLabServerPath? {
  val url = if (value.contains(URLUtil.SCHEME_SEPARATOR)) value else "${URLUtil.HTTPS_PROTOCOL}${URLUtil.SCHEME_SEPARATOR}$value"
  return runCatching {
    val uri = URI(url.trimEnd('/'))
    val host = requireNotNull(uri.host).lowercase()
    // Only the scheme and the host are case-insensitive, so the path keeps its case.
    GitLabServerPath(URI(uri.scheme.lowercase(), null, host, uri.port, uri.path, null, null).toString())
  }.getOrNull()
}