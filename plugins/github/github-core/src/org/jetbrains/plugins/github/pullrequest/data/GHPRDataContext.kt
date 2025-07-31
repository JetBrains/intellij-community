// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.icon.IconsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

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
  internal val interactionState: GHPRPersistentInteractionState,
) {
  init {
    scope.launchNow {
      listLoader.refreshOrReloadRequests.withInitial(Unit).collectScoped {
        try {
          listUpdatesChecker.start()
          awaitCancellation()
        }
        finally {
          listUpdatesChecker.stop()
        }
      }
    }

    dataProviderRepository.addDetailsLoadedListener(scope) { details: GHPullRequest ->
      listLoader.updateData {
        if (it.id == details.id) details else null
      }
    }
  }
}