// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

@ApiStatus.Internal
class GHPRDataContext internal constructor(
  val scope: CoroutineScope,
  internal val listLoader: GHPRListLoader,
  internal val listUpdatesChecker: GHPRListUpdatesChecker,
  internal val dataProviderRepository: GHPRDataProviderRepository,
  val securityService: GHPRSecurityService,
  val repositoryDataService: GHPRRepositoryDataService,
  internal val creationService: GHPRCreationService,
  internal val detailsService: GHPRDetailsService,
  internal val reactionsService: GHReactionsService,
  internal val htmlImageLoader: AsyncHtmlImageLoader,
  internal val avatarIconsProvider: GHAvatarIconsProvider,
  internal val reactionIconsProvider: IconsProvider<GHReactionContent>,
  internal val filesManager: GHPRFilesManager,
  internal val interactionState: GHPRPersistentInteractionState,
) {
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
      }
    }
  }
}