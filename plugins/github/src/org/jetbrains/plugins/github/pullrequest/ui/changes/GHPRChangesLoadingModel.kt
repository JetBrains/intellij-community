// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEventDispatcherLoadingModel
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

class GHPRChangesLoadingModel(val commitsModel: GHPRCommitsModel,
                              val cumulativeChangesModel: GHPRChangesModel,
                              val diffHelper: GHPRChangesDiffHelper)
  : GHEventDispatcherLoadingModel() {

  private val requestChangesListener = object : GHPRDataProvider.RequestsChangedListener {
    override fun commitsRequestChanged() {
      update()
    }
  }
  var dataProvider by Delegates.observable<GHPRDataProvider?>(null) { _, oldValue, newValue ->
    oldValue?.removeRequestsChangesListener(requestChangesListener)
    newValue?.addRequestsChangesListener(requestChangesListener)
    update()
  }

  private var updateFuture by Delegates.observable<CompletableFuture<Unit>?>(null) { _, oldValue, _ ->
    oldValue?.cancel(true)
  }

  override var loading: Boolean = false
    private set
  override var error: Throwable? = null
    private set
  override val resultAvailable: Boolean
    get() = cumulativeChangesModel.changes != null && commitsModel.commitsWithChanges != null

  init {
    update()
  }

  private fun update() {
    updateFuture = null
    val dataProvider = dataProvider
    if (dataProvider == null) {
      loading = false
      error = null
      commitsModel.commitsWithChanges = null
      cumulativeChangesModel.changes = null
      diffHelper.reset()
      eventDispatcher.multicaster.onReset()
    }
    else {
      loading = true
      error = null

      updateFuture = dataProvider.changesProviderRequest.handleOnEdt { changesProvider, error ->
        if (error == null) {
          commitsModel.commitsWithChanges = changesProvider.changesByCommits
          cumulativeChangesModel.changes = changesProvider.changes
          diffHelper.setUp(dataProvider, changesProvider)
        }
        this.error = error
        loading = false
        eventDispatcher.multicaster.onLoadingCompleted()
      }
      eventDispatcher.multicaster.onLoadingStarted()
    }
  }
}