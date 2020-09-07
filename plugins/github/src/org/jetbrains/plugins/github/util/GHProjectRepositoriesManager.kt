// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.AccountRemovedListener
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

@Service
class GHProjectRepositoriesManager(private val project: Project) : Disposable {

  private val updateQueue = MergingUpdateQueue("GitHub repositories update", 50, true, null, this, null, true)
    .usePassThroughInUnitTestMode()
  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var knownRepositories by observableField(emptySet<GHGitRepositoryMapping>(), eventDispatcher)
    private set

  private val serversFromDiscovery = HashSet<GithubServerPath>()

  init {
    updateRepositories()
  }

  fun findKnownRepositories(repository: GitRepository) = knownRepositories.filter {
    it.gitRemote.repository == repository
  }

  @CalledInAny
  private fun updateRepositories() {
    updateQueue.queue(Update.create(UPDATE_IDENTITY, ::doUpdateRepositories))
  }

  //TODO: execute on pooled thread - need to make GithubAccountManager ready
  @RequiresEdt
  private fun doUpdateRepositories() {
    LOG.debug("Repository list update started")
    val gitRepositories = project.service<GitRepositoryManager>().repositories
    if (gitRepositories.isEmpty()) {
      knownRepositories = emptySet()
      LOG.debug("No repositories found")
      return
    }

    val remotes = gitRepositories.flatMap { repo ->
      repo.remotes.flatMap { remote ->
        remote.urls.mapNotNull { url ->
          GitRemoteUrlCoordinates(url, remote, repo)
        }
      }
    }
    LOG.debug("Found remotes: $remotes")

    val authenticatedServers = service<GithubAccountManager>().accounts.map { it.server }
    val servers = mutableListOf<GithubServerPath>().apply {
      add(GithubServerPath.DEFAULT_SERVER)
      GithubAccountsMigrationHelper.getInstance().getOldServer()?.let { add(it) }
      addAll(authenticatedServers)
      addAll(serversFromDiscovery)
    }

    val repositories = HashSet<GHGitRepositoryMapping>()
    for (remote in remotes) {
      val repository = servers.find { it.matches(remote.url) }?.let { GHGitRepositoryMapping.create(it, remote) }
      if (repository != null) repositories.add(repository)
      else {
        scheduleEnterpriseServerDiscovery(remote)
      }
    }
    LOG.debug("New list of known repos: $repositories")
    knownRepositories = repositories

    for (server in authenticatedServers) {
      if (server.isGithubDotCom) continue
      service<GHEnterpriseServerMetadataLoader>().loadMetadata(server).successOnEdt {
        GHPRStatisticsCollector.logEnterpriseServerMeta(server, it)
      }
    }
  }

  @RequiresEdt
  private fun scheduleEnterpriseServerDiscovery(remote: GitRemoteUrlCoordinates) {
    val uri = GithubUrlUtil.getUriFromRemoteUrl(remote.url)
    LOG.debug("Extracted URI $uri from remote ${remote.url}")
    if (uri == null) return

    val host = uri.host ?: return
    val path = uri.path ?: return
    val pathParts = path.removePrefix("/").split('/').takeIf { it.size >= 2 } ?: return
    val serverSuffix = if (pathParts.size == 2) null else pathParts.subList(0, pathParts.size - 2).joinToString("/", "/")

    val server = GithubServerPath(false, host, null, serverSuffix)
    val serverHttp = GithubServerPath(true, host, null, serverSuffix)
    val server8080 = GithubServerPath(true, host, 8080, serverSuffix)
    LOG.debug("Scheduling GHE server discovery for $server, $serverHttp and $server8080")

    val serverManager = service<GHEnterpriseServerMetadataLoader>()
    serverManager.loadMetadata(server).successOnEdt {
      LOG.debug("Found GHE server at $server")
      serversFromDiscovery.add(server)
      doUpdateRepositories()
    }.errorOnEdt {
      serverManager.loadMetadata(serverHttp).successOnEdt {
        LOG.debug("Found GHE server at $serverHttp")
        serversFromDiscovery.add(serverHttp)
        doUpdateRepositories()
      }.errorOnEdt {
        serverManager.loadMetadata(server8080).successOnEdt {
          LOG.debug("Found GHE server at $server8080")
          serversFromDiscovery.add(server8080)
          doUpdateRepositories()
        }
      }
    }
  }

  fun addRepositoryListChangedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(eventDispatcher, disposable, listener)

  class RemoteUrlsListener(private val project: Project) : VcsRepositoryMappingListener, GitRepositoryChangeListener {
    override fun mappingChanged() = runInEdt(project) { updateRepositories(project) }
    override fun repositoryChanged(repository: GitRepository) = runInEdt(project) { updateRepositories(project) }
  }

  class AccountsListener : AccountRemovedListener, AccountTokenChangedListener {
    override fun accountRemoved(removedAccount: GithubAccount) = updateRemotes()
    override fun tokenChanged(account: GithubAccount) = updateRemotes()

    private fun updateRemotes() = runInEdt {
      for (project in ProjectManager.getInstance().openProjects) {
        updateRepositories(project)
      }
    }
  }

  companion object {
    private val LOG = logger<GHProjectRepositoriesManager>()

    private val UPDATE_IDENTITY = Any()

    private inline fun runInEdt(project: Project, crossinline runnable: () -> Unit) {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) runnable()
      else application.invokeLater({ runnable() }) { project.isDisposed }
    }

    private fun updateRepositories(project: Project) {
      try {
        if (!project.isDisposed) project.service<GHProjectRepositoriesManager>().updateRepositories()
      }
      catch (e: Exception) {
        LOG.info("Error occurred while updating repositories", e)
      }
    }
  }

  override fun dispose() {}
}