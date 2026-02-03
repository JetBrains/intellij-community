// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.searcheverywhere.SplitSearchListener
import com.intellij.platform.searchEverywhere.providers.SeLog
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@ApiStatus.Internal
class SeSearchStatePublisher {
  private val listeners = mutableListOf<SplitSearchListener>()
  private val lock = ReentrantLock()
  private var currentSearchId: String? = null

  fun searchStarted(searchId: String, pattern: String, tabId: String) {
    lock.withLock {
      currentSearchId?.let {
        searchStoppedProducingResults(it, -1, true)
      }

      SeLog.log(SeLog.LISTENERS) { "Search started: $searchId, $pattern, $tabId" }
      currentSearchId = searchId
      listeners.forEach { it.searchStarted(pattern, tabId) }
    }
  }

  fun elementsAdded(searchId: String, uuidToElement: Map<String, Any>) {
    lock.withLock {
      if (searchId != currentSearchId) {
        SeLog.log(SeLog.LISTENERS) { "Added elements tried to be published to listeners for wrong search: $searchId, current search: $currentSearchId" }
        return@withLock
      }
      SeLog.log(SeLog.LISTENERS) { "Elements added: $searchId, ${uuidToElement.size}" }
      listeners.forEach { it.elementsAdded(uuidToElement) }
    }
  }

  fun elementsRemoved(searchId: String, count: Int) {
    lock.withLock {
      if (searchId != currentSearchId) {
        SeLog.log(SeLog.LISTENERS) { "Removed elements tried to be published to listeners for wrong search: $searchId, current search: $currentSearchId" }
        return@withLock
      }
      SeLog.log(SeLog.LISTENERS) { "Elements removed: $searchId, $count" }
      listeners.forEach { it.elementsRemoved(count) }
    }
  }

  fun searchStoppedProducingResults(searchId: String, count: Int, isFinished: Boolean) {
    lock.withLock {
      if (currentSearchId != null && searchId != currentSearchId) {
        SeLog.log(SeLog.LISTENERS) { "Finished search tried to be published to listeners for wrong search: $searchId, current search: $currentSearchId" }
        return@withLock
      }

      SeLog.log(SeLog.LISTENERS) { "Search ${if (isFinished) "stopped" else "paused"} producing results: $searchId, $count" }
      listeners.forEach { it.searchFinished(count) }

      if (isFinished) currentSearchId = null
    }
  }

  fun addListener(listener: SplitSearchListener) {
    lock.withLock {
      listeners.add(listener)
    }
  }

  fun removeListener(listener: SplitSearchListener) {
    lock.withLock {
      listeners.remove(listener)
    }
  }
}
