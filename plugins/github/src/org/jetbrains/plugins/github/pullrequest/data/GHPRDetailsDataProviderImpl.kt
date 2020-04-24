// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

class GHPRDetailsDataProviderImpl(private val detailsService: GHPRDetailsService,
                                  private val pullRequestId: GHPRIdentifier)
  : GHPRDetailsDataProvider, Disposable {

  private val detailsLoadedEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var loadedDetails by Delegates.observable<GHPullRequest?>(null) { _, _, _ ->
    detailsLoadedEventDispatcher.multicaster.eventOccurred()
  }
    private set

  private val detailsRequestValue = LazyCancellableBackgroundProcessValue.create { indicator ->
    detailsService.loadDetails(indicator, pullRequestId).successOnEdt {
      loadedDetails = it
      it
    }
  }

  override fun loadDetails(): CompletableFuture<GHPullRequest> = detailsRequestValue.value

  override fun reloadDetails() = detailsRequestValue.drop()

  override fun addDetailsReloadListener(disposable: Disposable, listener: () -> Unit) =
    detailsRequestValue.addDropEventListener(disposable, listener)

  override fun addDetailsLoadedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(detailsLoadedEventDispatcher, disposable, listener)

  override fun dispose() {
    detailsRequestValue.drop()
  }
}