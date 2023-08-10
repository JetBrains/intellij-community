// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.ui.SingleSelectionModel
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.Affiliation
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import javax.swing.ListSelectionModel

internal class GHCloneDialogRepositoryListLoaderImpl : GHCloneDialogRepositoryListLoader, Disposable {

  override val loading: Boolean
    get() = indicatorsMap.isNotEmpty()
  private val loadingEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override val listModel = GHCloneDialogRepositoryListModel()
  override val listSelectionModel = SingleSelectionModel()

  private val indicatorsMap = mutableMapOf<GithubAccount, ProgressIndicator>()

  override fun loadRepositories(account: GithubAccount) {
    if (indicatorsMap.containsKey(account)) return

    val indicator = EmptyProgressIndicator()
    indicatorsMap[account] = indicator
    loadingEventDispatcher.multicaster.eventOccurred()

    ProgressManager.getInstance().submitIOTask(indicator) {
      val token = runBlocking { service<GHAccountManager>().findCredentials(account) } ?: throw GithubMissingTokenException(account)
      val executor = service<GithubApiRequestExecutor.Factory>().create(token)

      val details = executor.execute(indicator, GithubApiRequests.CurrentUser.get(account.server))

      val repoPagesRequest = GithubApiRequests.CurrentUser.Repos.pages(account.server,
                                                                       affiliation = Affiliation.combine(Affiliation.OWNER,
                                                                                                         Affiliation.COLLABORATOR),
                                                                       pagination = GithubRequestPagination.DEFAULT)
      val pageItemsConsumer: (List<GithubRepo>) -> Unit = {
        indicator.checkCanceled()
        runInEdt {
          indicator.checkCanceled()
          preservingSelection(listModel, listSelectionModel) {
            listModel.addRepositories(account, details, it)
          }
        }
      }
      GithubApiPagesLoader.loadAll(executor, indicator, repoPagesRequest, pageItemsConsumer)

      val orgsRequest = GithubApiRequests.CurrentUser.Orgs.pages(account.server)
      val userOrganizations = GithubApiPagesLoader.loadAll(executor, indicator, orgsRequest).sortedBy { it.login }

      for (org in userOrganizations) {
        val orgRepoRequest = GithubApiRequests.Organisations.Repos.pages(account.server, org.login, GithubRequestPagination.DEFAULT)
        GithubApiPagesLoader.loadAll(executor, indicator, orgRepoRequest, pageItemsConsumer)
      }
    }.whenComplete { _, _ ->
      indicatorsMap.remove(account)
      loadingEventDispatcher.multicaster.eventOccurred()
    }.errorOnEdt(ModalityState.any()) {
      preservingSelection(listModel, listSelectionModel) {
        listModel.setError(account, it)
      }
    }
  }

  override fun clear(account: GithubAccount) {
    indicatorsMap[account]?.cancel()
    listModel.clear(account)
    loadingEventDispatcher.multicaster.eventOccurred()
  }

  override fun addLoadingStateListener(listener: () -> Unit) = SimpleEventListener.addListener(loadingEventDispatcher, listener)

  override fun dispose() {
    indicatorsMap.forEach { (_, indicator) -> indicator.cancel() }
    loadingEventDispatcher.multicaster.eventOccurred()
  }

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