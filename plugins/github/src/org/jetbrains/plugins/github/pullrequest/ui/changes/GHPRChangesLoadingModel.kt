// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEventDispatcherLoadingModel
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

class GHPRChangesLoadingModel(private val changesModel: GHPRChangesModel,
                              private val diffHelper: GHPRChangesDiffHelper,
                              zipChanges: Boolean)
  : GHEventDispatcherLoadingModel() {

  var zipChanges by Delegates.observable(zipChanges) { _, _, _ ->
    update()
  }
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
    get() = changesModel.changes != null || changesModel.commits != null

  init {
    update()
  }

  private fun update() {
    updateFuture = null
    val dataProvider = dataProvider
    if (dataProvider == null) {
      loading = false
      error = null
      changesModel.commits = null
      changesModel.changes = null
      diffHelper.reset()
      eventDispatcher.multicaster.onReset()
    }
    else {
      loading = true
      error = null

      updateFuture = dataProvider.changesProviderRequest.successOnEdt {
        if (zipChanges) changesModel.changes = it.changes
        else changesModel.commits = it.changesByCommits

        diffHelper.setUp(dataProvider, it)
      }.errorOnEdt {
        this.error = error
      }.handleOnEdt { _, _ ->
        loading = false
        eventDispatcher.multicaster.onLoadingCompleted()
      }
      eventDispatcher.multicaster.onLoadingStarted()
    }
  }
}