// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

abstract class GHSimpleLoadingModel<T> : GHEventDispatcherLoadingModel() {
  override var loading: Boolean = false
    protected set
  override var resultAvailable: Boolean = false
    protected set
  override var error: Throwable? = null
    protected set
  var result: T? = null
    protected set
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> GHSimpleLoadingModel<T>.getResultFlow(): Flow<T?> =
  callbackFlow {
    val emit = { trySend(result.takeIf { resultAvailable }) }
    val listener = object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        emit()
      }
    }

    emit() // initial value

    addStateChangeListener(listener)
    awaitClose { removeStateChangeListener(listener) }
  }