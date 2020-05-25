// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchQueryHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

internal class GHPRDataContext(val gitRepositoryCoordinates: GitRemoteUrlCoordinates,
                               val repositoryCoordinates: GHRepositoryCoordinates,
                               val account: GithubAccount,
                               val requestExecutor: GithubApiRequestExecutor,
                               val searchHolder: GHPRSearchQueryHolder,
                               val listLoader: GHListLoader<GHPullRequestShort>,
                               val listUpdatesChecker: GHPRListUpdatesChecker,
                               val dataProviderRepository: GHPRDataProviderRepository,
                               val securityService: GHPRSecurityService,
                               val repositoryDataService: GHPRRepositoryDataService) : Disposable {

  private val listenersDisposable = Disposer.newDisposable("GH PR context listeners disposable")

  init {
    searchHolder.addQueryChangeListener(listenersDisposable) {
      listLoader.reset()
    }
    listLoader.addDataListener(listenersDisposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) = listUpdatesChecker.start()
      override fun onAllDataRemoved() = listUpdatesChecker.stop()
    })
    dataProviderRepository.addDetailsLoadedListener(listenersDisposable) { details: GHPullRequest ->
      listLoader.updateData(details)
    }
  }

  override fun dispose() {
    Disposer.dispose(listenersDisposable)
    Disposer.dispose(dataProviderRepository)
    Disposer.dispose(listLoader)
    Disposer.dispose(listUpdatesChecker)
    Disposer.dispose(repositoryDataService)
  }
}
