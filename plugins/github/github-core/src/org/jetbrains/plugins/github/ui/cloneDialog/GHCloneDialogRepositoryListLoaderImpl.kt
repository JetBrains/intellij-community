// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.api.page.foldToList
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SingleSelectionModel
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.Affiliation
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import javax.swing.ListSelectionModel
import kotlin.properties.Delegates

internal class GHCloneDialogRepositoryListLoaderImpl(parentCs: CoroutineScope) : GHCloneDialogRepositoryListLoader {

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

  private var loaderCount by Delegates.observable(0) { _, _, _ ->
    loadingEventDispatcher.multicaster.eventOccurred()
  }
  override val loading: Boolean get() = loaderCount > 0
  private val loadingEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override val listModel = GHCloneDialogRepositoryListModel()
  override val listSelectionModel = SingleSelectionModel()

  private val jobsMap = mutableMapOf<GithubAccount, Job>()

  @RequiresEdt
  override fun loadRepositories(account: GithubAccount) {
    val currentJob = jobsMap[account]
    if (currentJob != null && !currentJob.isCancelled && !currentJob.isCompleted) return

    jobsMap[account] = cs.launchNow {
      loaderCount++
      try {
        doLoadRepositories(account)
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        preservingSelection(listModel, listSelectionModel) {
          listModel.setError(account, e)
        }
      }
      finally {
        loaderCount--
      }
    }
  }

  private suspend fun doLoadRepositories(account: GithubAccount) {
    withContext(Dispatchers.IO) {
      val token = serviceAsync<GHAccountManager>().findCredentials(account) ?: throw GithubMissingTokenException(account)
      val executor = serviceAsync<GithubApiRequestExecutor.Factory>().create(account.server, token)
      val details = executor.executeSuspend(GithubApiRequests.CurrentUser.get(account.server))
      val repoPagesRequest = GithubApiRequests.CurrentUser.Repos.pages(account.server,
                                                                       affiliations = setOf(Affiliation.OWNER, Affiliation.COLLABORATOR),
                                                                       pagination = GithubRequestPagination.DEFAULT)


      val repoCollector = FlowCollector<List<GithubRepo>> {
        withContext(Dispatchers.Main) {
          checkCanceled()
          preservingSelection(listModel, listSelectionModel) {
            listModel.addRepositories(account, details, it)
          }
        }
      }

      GithubApiPagesLoader.batchesFlow(executor, repoPagesRequest).collect(repoCollector)

      val orgsRequest = GithubApiRequests.CurrentUser.Orgs.pages(account.server)
      val userOrganizations = GithubApiPagesLoader.batchesFlow(executor, orgsRequest).foldToList().sortedBy { it.login }

      for (org in userOrganizations) {
        val orgRepoRequest = GithubApiRequests.Organisations.Repos.pages(account.server, org.login, GithubRequestPagination.DEFAULT)
        GithubApiPagesLoader.batchesFlow(executor, orgRepoRequest).collect(repoCollector)
      }
    }
  }

  override fun clear(account: GithubAccount) {
    jobsMap.remove(account)?.cancel()
    listModel.clear(account)
  }

  override fun addLoadingStateListener(listener: () -> Unit) = SimpleEventListener.addListener(loadingEventDispatcher, listener)

  companion object {
    private fun preservingSelection(listModel: GHCloneDialogRepositoryListModel, selectionModel: ListSelectionModel, action: () -> Unit) {
      val selection = if (selectionModel.isSelectionEmpty) {
        null
      }
      else {
        selectionModel.leadSelectionIndex.let {
          if (it < 0 || listModel.size == 0) null
          else listModel.getItemAt(it)
        }
      }
      action()
      if (selection != null) {
        val (account, item) = selection
        val newIdx = listModel.indexOf(account, item)
        if (newIdx >= 0) {
          selectionModel.setSelectionInterval(newIdx, newIdx)
        }
      }
    }
  }
}