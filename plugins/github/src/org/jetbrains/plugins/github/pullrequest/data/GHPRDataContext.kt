// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRDiffRequestModel
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCreationService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

internal class GHPRDataContext(val scope: CoroutineScope,
                               val listLoader: GHPRListLoader,
                               val listUpdatesChecker: GHPRListUpdatesChecker,
                               val dataProviderRepository: GHPRDataProviderRepository,
                               val securityService: GHPRSecurityService,
                               val repositoryDataService: GHPRRepositoryDataService,
                               val creationService: GHPRCreationService,
                               val detailsService: GHPRDetailsService,
                               val htmlImageLoader: AsyncHtmlImageLoader,
                               val avatarIconsProvider: GHAvatarIconsProvider,
                               val filesManager: GHPRFilesManager,
                               val newPRDiffModel: GHPRDiffRequestModel) {

  private val listenersDisposable = Disposer.newDisposable("GH PR context listeners disposable")

  init {
    listLoader.addDataListener(listenersDisposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) = listUpdatesChecker.start()
      override fun onAllDataRemoved() = listUpdatesChecker.stop()
    })
    dataProviderRepository.addDetailsLoadedListener(listenersDisposable) { details: GHPullRequest ->
      listLoader.updateData {
        if (it.id == details.id) details else null
      }
      filesManager.updateTimelineFilePresentation(details)
    }

    // need immediate to dispose in time
    scope.launch(Dispatchers.Main.immediate) {
      try {
        awaitCancellation()
      }
      finally {
        Disposer.dispose(filesManager)
        Disposer.dispose(listenersDisposable)
        Disposer.dispose(dataProviderRepository)
        Disposer.dispose(listLoader)
        Disposer.dispose(listUpdatesChecker)
        Disposer.dispose(repositoryDataService)
      }
    }
  }
}