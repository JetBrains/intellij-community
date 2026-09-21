// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.system.PosixIds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
object SuperUserStatus {

  /**
   * Returns `null` if the super user status is not yet computed.
   * Use [whenKnown] for a callback when the status is known
   */
  @JvmStatic
  @get:Contract(pure = false)
  val isSuperUserOrNull: Boolean?
    get() {
      request.start()
      if (request.isCompleted) {
        @OptIn(ExperimentalCoroutinesApi::class)
        return request.getCompleted()
      } else {
        return null
      }
    }

  /**
   * Blocks until super user status is computed
   */
  @JvmStatic
  val isSuperUser: Boolean
    get() {
      request.start()
      return request.asCompletableFuture().get()
    }

  fun whenKnown(@RequiresBackgroundThread action: suspend () -> Unit): Job {
    @OptIn(DelicateCoroutinesApi::class)
    return GlobalScope.launch(Dispatchers.IO) {
      request.join()
      action()
    }
  }

  @OptIn(DelicateCoroutinesApi::class, LowLevelLocalMachineAccess::class)
  private val request: Deferred<Boolean> = GlobalScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    try {
      when {
        OS.CURRENT == OS.Windows -> WindowsElevationStatus.isElevated()
        else -> UnixUserStatus.isSuperUser()
      }
    }
    catch (t: Throwable) {
      logger<SuperUserStatus>().warn(t)
      false
    }
  }
}

//<editor-fold desc="Windows implementation">
private object WindowsElevationStatus {
  fun isElevated(): Boolean = WindowsTokenElevation.isElevated()
}
//</editor-fold>

//<editor-fold desc="Unix implementation">
private object UnixUserStatus {
  @OptIn(LowLevelLocalMachineAccess::class)
  fun isSuperUser(): Boolean = PosixIds.geteuid() == 0
}
//</editor-fold>
