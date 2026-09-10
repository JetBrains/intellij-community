// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * A publisher fires this topic when the model under the Search Everywhere results changes.
 * Each active tab then re-runs its current query and refreshes the results in place.
 * A burst of events re-runs the query once, after the last event.
 *
 * The only subscriber is the frontend tab view model, so the topic must be published on the frontend project bus.
 * In split mode a backend publisher reaches nobody; such a publisher needs its own RPC to the frontend.
 */
@ApiStatus.Internal
fun interface SeSearchRestartListener {
  fun restartSearch()

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<SeSearchRestartListener> = Topic(SeSearchRestartListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
