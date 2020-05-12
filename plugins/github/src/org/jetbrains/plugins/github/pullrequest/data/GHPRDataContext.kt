// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import javax.swing.ListModel

internal class GHPRDataContext(val gitRepositoryCoordinates: GitRemoteUrlCoordinates,
                               val repositoryCoordinates: GHRepositoryCoordinates,
                               val account: GithubAccount,
                               val requestExecutor: GithubApiRequestExecutor,
                               val listModel: ListModel<GHPullRequestShort>,
                               val searchHolder: GithubPullRequestSearchQueryHolder,
                               val listLoader: GHPRListLoader,
                               val dataLoader: GHPRDataLoader,
                               val securityService: GHPRSecurityService,
                               val repositoryDataService: GHPRRepositoryDataService) : Disposable {

  override fun dispose() {
    Disposer.dispose(dataLoader)
    Disposer.dispose(listLoader)
    Disposer.dispose(repositoryDataService)
  }
}
