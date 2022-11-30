// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.Application
import com.intellij.openapi.util.Key

sealed interface WarmupStatus {
  companion object {
    private val key = Key<WarmupStatus>("intellij.warmup.status")

    fun currentStatus(app: Application): WarmupStatus {
      return app.getUserData(key) ?: NotStarted
    }

    internal fun statusChanged(app: Application, newStatus: WarmupStatus) {
      app.putUserData(key, newStatus)
    }
  }

  object NotStarted: WarmupStatus
  object InProgress: WarmupStatus
  data class Finished(val indexedFileCount: Int): WarmupStatus
}