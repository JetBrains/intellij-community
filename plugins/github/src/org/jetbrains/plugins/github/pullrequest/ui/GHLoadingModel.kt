// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import java.util.*

interface GHLoadingModel {
  val loading: Boolean

  val resultAvailable: Boolean
  val error: Throwable?

  fun addStateChangeListener(listener: StateChangeListener)

  @JvmDefaultWithCompatibility
  interface StateChangeListener : EventListener {
    fun onLoadingStarted() {}
    fun onLoadingCompleted() {}
    fun onReset() {
      onLoadingCompleted()
    }
  }
}