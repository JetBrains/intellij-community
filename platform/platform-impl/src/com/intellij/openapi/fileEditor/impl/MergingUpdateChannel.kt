// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.diagnostic.logger
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.time.Duration

internal class MergingUpdateChannel<T>(private val delay: Duration, private val update: suspend (Collection<T>) -> Unit) {
  private val updateChannel = Channel<T>(Channel.UNLIMITED)

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun start(receiveFilter: (T) -> Boolean = { true }) {
    // don't waste time to compare VirtualFile -
    // better to update several times the equal VirtualFiles than invoking a potentially expensive comparison
    val toUpdate = ReferenceLinkedOpenHashSet<T>()
    while (true) {
      delay(delay)

      toUpdate.clear()
      // collect unique collection
      // do-while - avoid calling isEmpty each 50ms, suspend to get the first item
      do {
        val file = updateChannel.receive()
        if (receiveFilter(file)) {
          toUpdate.add(file)
        }
      }
      while (!updateChannel.isEmpty)

      if (toUpdate.isEmpty()) {
        continue
      }

      try {
        update(toUpdate)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<MergingUpdateChannel<*>>().error(e)
      }
    }
  }

  fun stop() {
    updateChannel.close()
  }

  fun queue(item: T) {
    check(!updateChannel.trySend(item).isFailure)
  }
}