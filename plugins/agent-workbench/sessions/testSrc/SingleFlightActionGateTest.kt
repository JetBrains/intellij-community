// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightActionGateTest {
  @Test
  fun dropSkipsDuplicateActionWhileKeyIsInFlight() {
    runBlocking(Dispatchers.Default) {
      val gate = SingleFlightActionGate()
      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val runCount = AtomicInteger(0)
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()

      try {
        val first = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.DROP) {
          runCount.incrementAndGet()
          started.complete(Unit)
          release.await()
        }
        started.await()

        val duplicate = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.DROP) {
          runCount.incrementAndGet()
        }

        assertThat(first).isNotNull()
        assertThat(duplicate).isNull()
        assertThat(gate.isInFlight("open:thread-1")).isTrue()

        release.complete(Unit)
        first?.join()

        assertThat(runCount.get()).isEqualTo(1)
        assertThat(gate.isInFlight("open:thread-1")).isFalse()
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun dropAllowsDifferentKeysToRunConcurrently() {
    runBlocking(Dispatchers.Default) {
      val gate = SingleFlightActionGate()
      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val runCount = AtomicInteger(0)

      try {
        val first = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.DROP) {
          runCount.incrementAndGet()
          started.complete(Unit)
          release.await()
        }
        started.await()

        val second = gate.launch(scope = scope, key = "open:thread-2", policy = SingleFlightPolicy.DROP) {
          runCount.incrementAndGet()
        }

        assertThat(first).isNotNull()
        assertThat(second).isNotNull()

        release.complete(Unit)
        first?.join()
        second?.join()

        assertThat(runCount.get()).isEqualTo(2)
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun dropReleasesKeyAfterCancellation() {
    runBlocking(Dispatchers.Default) {
      val gate = SingleFlightActionGate()
      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()

      try {
        val canceled = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.DROP) {
          started.complete(Unit)
          release.await()
        }
        started.await()
        canceled?.cancel()
        canceled?.join()

        assertThat(gate.isInFlight("open:thread-1")).isFalse()

        val retried = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.DROP) { }
        assertThat(retried).isNotNull()
        retried?.join()
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun restartLatestRunsOnlyMostRecentPendingAction() {
    runBlocking(Dispatchers.Default) {
      val gate = SingleFlightActionGate()
      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val release = CompletableDeferred<Unit>()
      val started = CompletableDeferred<Unit>()
      val runs = mutableListOf<String>()

      try {
        val first = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.RESTART_LATEST) {
          started.complete(Unit)
          runs += "first"
          release.await()
        }
        started.await()

        val second = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.RESTART_LATEST) {
          runs += "second"
        }
        val third = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.RESTART_LATEST) {
          runs += "third"
        }

        assertThat(first).isNotNull()
        assertThat(second).isNull()
        assertThat(third).isNull()

        release.complete(Unit)
        first?.join()

        assertThat(runs).containsExactly("first", "third")
        assertThat(gate.isInFlight("open:thread-1")).isFalse()
      }
      finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun queueRunsPendingActionsInOrder() {
    runBlocking(Dispatchers.Default) {
      val gate = SingleFlightActionGate()
      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val release = CompletableDeferred<Unit>()
      val started = CompletableDeferred<Unit>()
      val runs = mutableListOf<String>()

      try {
        val first = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.QUEUE) {
          started.complete(Unit)
          runs += "first"
          release.await()
        }
        started.await()

        val second = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.QUEUE) {
          runs += "second"
        }
        val third = gate.launch(scope = scope, key = "open:thread-1", policy = SingleFlightPolicy.QUEUE) {
          runs += "third"
        }

        assertThat(first).isNotNull()
        assertThat(second).isNull()
        assertThat(third).isNull()

        release.complete(Unit)
        first?.join()

        assertThat(runs).containsExactly("first", "second", "third")
        assertThat(gate.isInFlight("open:thread-1")).isFalse()
      }
      finally {
        scope.cancel()
      }
    }
  }
}
