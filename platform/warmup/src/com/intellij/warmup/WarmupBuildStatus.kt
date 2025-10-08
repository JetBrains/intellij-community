// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

sealed interface WarmupBuildStatus {
  companion object {
    private val key = Key<InvocationStatus>("intellij.warmup.build.status")

    fun currentStatus(): WarmupBuildStatus {
      return ApplicationManager.getApplication().getUserData(key) ?: NotInvoked
    }

    internal fun statusChanged(newStatus: InvocationStatus) {
      ApplicationManager.getApplication().putUserData(key, newStatus)
    }
  }

  object NotInvoked : WarmupBuildStatus

  sealed class InvocationStatus(val message: @NonNls String) : WarmupBuildStatus
  class Success @ApiStatus.Internal constructor(message: @NonNls String) : InvocationStatus(message)
  class Failure @ApiStatus.Internal constructor(message: @NonNls String) : InvocationStatus(message)
}