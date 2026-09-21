// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.database.impl.housekeeping.DatabaseHousekeepingCoordinator
import com.intellij.platform.util.io.storages.database.impl.housekeeping.HousekeepingRegistration
import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper
import com.intellij.util.ConcurrencyUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

class DatabaseHousekeepingCoordinatorTest {
  @Test
  fun `runs a housekeeper`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      var runs = 0
      val registration = coordinator.register(Housekeeper {
        runs++
        TEST_DELAY
      })

      assertEquals(TEST_DELAY, coordinator.runHousekeeping(registration))
      assertTrue(runs == 1, "Housekeeping must run exactly once")
    }
  }

  @Test
  fun `rejects a concurrent run for the same registration`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      val housekeeper = BlockingHousekeeper()
      val registration = coordinator.register(housekeeper)
      Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
        val firstRun = executor.submit { coordinator.runHousekeeping(registration) }
        assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "The first run must start")

        assertThrows(IllegalStateException::class.java, { coordinator.runHousekeeping(registration) },
                     "A registration must allow only one active run")

        housekeeper.release.countDown()
        firstRun.get(10, TimeUnit.SECONDS)
      }
    }
  }

  @Test
  fun `closing a registration cancels and waits for its run`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      val housekeeper = BlockingHousekeeper()
      val registration = coordinator.register(housekeeper)
      Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
        val run = executor.submit { coordinator.runHousekeeping(registration) }
        assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "Housekeeping must start before registration close")

        val close = executor.submit { registration.close() }
        assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "Registration close must cancel the active run")
        assertFalse(close.isDone, "Registration close must wait for the active run")

        housekeeper.release.countDown()
        run.get(10, TimeUnit.SECONDS)
        close.get(10, TimeUnit.SECONDS)
        assertThrows(IllegalStateException::class.java, { coordinator.runHousekeeping(registration) },
                     "A closed registration must reject new runs")
      }
    }
  }

  @Test
  fun `closing the coordinator closes registrations and rejects new registrations`() {
    val coordinator = DatabaseHousekeepingCoordinator()
    val housekeeper = BlockingHousekeeper()
    val registration = coordinator.register(housekeeper)
    Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
      val run = executor.submit { coordinator.runHousekeeping(registration) }
      assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "Housekeeping must start before coordinator close")

      val close = executor.submit { coordinator.close() }
      assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "Coordinator close must cancel registered housekeepers")
      assertFalse(close.isDone, "Coordinator close must wait for registered housekeepers")

      housekeeper.release.countDown()
      run.get(10, TimeUnit.SECONDS)
      close.get(10, TimeUnit.SECONDS)
    }

    assertThrows(IllegalStateException::class.java, { coordinator.register(Housekeeper { TEST_DELAY }) },
                 "A closed coordinator must reject new registrations")
  }

  @Test
  fun `housekeeper failure does not prevent the next run`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      var runs = 0
      val registration = coordinator.register(Housekeeper {
        if (runs++ == 0) throw IOException("Expected failure")
        TEST_DELAY
      })

      assertThrows(IOException::class.java, { coordinator.runHousekeeping(registration) },
                   "A housekeeping failure must reach the caller")
      coordinator.runHousekeeping(registration)
      assertEquals(2, runs, "The same housekeeper must run again after a failure")
    }
  }

  @Test
  fun `rejects a non-positive next run delay`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      val registration = coordinator.register(Housekeeper { Duration.ZERO })

      val failure = assertThrows(IllegalArgumentException::class.java) {
        coordinator.runHousekeeping(registration)
      }
      assertTrue(failure.message.orEmpty().contains("must be positive"), "The failure must explain the delay contract")
    }
  }

  @Test
  fun `cancellation belongs only to the active run`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      val tokens = mutableListOf<BooleanSupplier>()
      val housekeeper = BlockingHousekeeper()
      val registration = coordinator.register(Housekeeper { cancellationRequested ->
        tokens.add(cancellationRequested)
        if (tokens.size == 2) housekeeper.runHousekeeping(cancellationRequested) else TEST_DELAY
      })
      coordinator.runHousekeeping(registration)

      Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
        val run = executor.submit { coordinator.runHousekeeping(registration) }
        assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "The second run must start")
        assertFalse(tokens.last().asBoolean, "A new run must start without cancellation")

        val close = executor.submit { registration.close() }
        try {
          assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "Closing must cancel the active run")
          assertTrue(tokens.last().asBoolean, "The active run must observe cancellation")
          assertFalse(tokens.first().asBoolean, "Closing must not change the token of a completed run")
        }
        finally {
          housekeeper.release.countDown()
        }
        run.get(10, TimeUnit.SECONDS)
        close.get(10, TimeUnit.SECONDS)
      }
    }
  }

  @Test
  fun `housekeeper cannot close its own registration during a run`() {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      lateinit var registration: HousekeepingRegistration
      registration = coordinator.register(Housekeeper {
        assertThrows(IllegalStateException::class.java, { registration.close() },
                     "Closing from the active run must fail instead of waiting for itself")
        TEST_DELAY
      })

      coordinator.runHousekeeping(registration)
      registration.close()
    }
  }

  @Test
  fun `rejects a registration from another coordinator`() {
    DatabaseHousekeepingCoordinator().use { first ->
      DatabaseHousekeepingCoordinator().use { second ->
        val registration = first.register(Housekeeper { TEST_DELAY })

        assertThrows(IllegalArgumentException::class.java, { second.runHousekeeping(registration) },
                     "A coordinator must reject a foreign registration")
      }
    }
  }

  private class BlockingHousekeeper(private val failureOnCancellation: IOException? = null) : Housekeeper {
    val started = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val release = CountDownLatch(1)
    @Volatile
    var threadName: String? = null

    override fun runHousekeeping(cancellationRequested: BooleanSupplier): Duration {
      threadName = Thread.currentThread().name
      started.countDown()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      do {
        if (cancellationRequested.asBoolean) {
          cancelled.countDown()
          failureOnCancellation?.let { throw it }
        }
        if (release.await(10, TimeUnit.MILLISECONDS)) return TEST_DELAY
      }
      while (System.nanoTime() < deadline)
      throw AssertionError("The test must release the housekeeper")
    }
  }

  companion object {
    private val TEST_DELAY: Duration = Duration.ofDays(1)
  }
}
