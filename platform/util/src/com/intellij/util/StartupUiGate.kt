// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Holds the startup work that the first project frame does not need before its editors are shown.
 * The application start arms the gate when the property `ide.startup.ui.gate` is true. The gate opens when the
 * first project has restored and focused its editors, or when the IDE opens no project, or after a timeout.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
object StartupUiGate {
  private val armed = AtomicBoolean()
  private val editorsShown = CompletableDeferred<Unit>()

  fun arm() {
    armed.set(true)
  }

  val isReady: Boolean
    get() = !armed.get() || editorsShown.isCompleted

  fun markEditorsShown() {
    editorsShown.complete(Unit)
  }

  /**
   * Waits until the gate opens. Do not call it on a path that the project opening waits for, such as
   * `ApplicationInitializedListener.execute`, because the gate opens only after the project frame paints.
   * Launch the gated work in a coroutine scope there.
   */
  suspend fun awaitEditorsShown() {
    if (!isReady) {
      withTimeoutOrNull(5.seconds) { editorsShown.await() }
    }
  }
}
