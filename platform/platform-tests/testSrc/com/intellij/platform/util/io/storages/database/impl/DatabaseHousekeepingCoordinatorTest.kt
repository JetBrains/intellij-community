// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.impl.housekeeping.DatabaseHousekeepingCoordinator
import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper
import com.intellij.platform.util.io.storages.database.impl.housekeeping.HousekeepingRegistration
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase
import com.intellij.platform.util.io.storages.database.spi.BlocksStore
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.SEALED
import com.intellij.platform.util.io.storages.database.storages.durablemap.RetireUnusedBlockInDurableMapHousekeeper
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.DATA
import com.intellij.util.ConcurrencyUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

@Suppress("SuspiciousPackagePrivateAccess")
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

  @Test
  fun `scheduler uses each housekeeper delay without owning its executors`() {
    val blocksDatabase = TrackingBlocksDatabase()
    val database = DurableDatabaseImpl(blocksDatabase)
    val fastRuns = CountDownLatch(2)
    val slowRun = CountDownLatch(1)
    var slowRunCount = 0
    database.registerHousekeeper(Housekeeper {
      fastRuns.countDown()
      Duration.ofMillis(1)
    })

    Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping scheduler")).use { scheduler ->
      Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping worker")).use { worker ->
        database.startHousekeeping(scheduler, worker).use {
          database.registerHousekeeper(Housekeeper {
            slowRunCount++
            slowRun.countDown()
            TEST_DELAY
          })
          assertTrue(slowRun.await(10, TimeUnit.SECONDS), "The scheduler must run each new registration")
          assertTrue(fastRuns.await(10, TimeUnit.SECONDS), "The scheduler must use the returned delay")
          assertEquals(1, slowRunCount, "A long delay must prevent a second slow run")
        }
        assertFalse(scheduler.isShutdown, "The database scheduler must not own the scheduling executor")
        assertFalse(worker.isShutdown, "The database scheduler must not own the housekeeping executor")
        database.close()
      }
    }
  }

  @Test
  fun `factory wrapper delegates database operations and leaves its executors open`(@TempDir directory: Path) {
    Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping scheduler")).use { scheduler ->
      Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping worker")).use { worker ->
        val factory = DurableDatabaseFactory(1024 * 1024)
          .housekeeping(scheduler, worker)
        val reconfiguredFactory = factory.fsyncOnClose(false)
        assertSame(factory.housekeepingConfiguration(), reconfiguredFactory.housekeepingConfiguration(),
                   "Other factory settings must preserve housekeeping")
        val database = reconfiguredFactory.open(directory)
        database.use {
          val descriptor = stringAsUTF8()
          val map = database.openMap("map", 1, descriptor, descriptor)
          map.put("key", "value")
          assertEquals("value", map.get("key"))
        }

        assertTrue(database.isClosed, "Closing the wrapper must close the database")
        assertFalse(scheduler.isShutdown, "The wrapper must not own the scheduling executor")
        assertFalse(worker.isShutdown, "The wrapper must not own the housekeeping executor")
      }
    }
  }

  @Test
  fun `database releases its lock before a housekeeping round starts`() {
    val blocksDatabase = TrackingBlocksDatabase()
    val database = DurableDatabaseImpl(blocksDatabase)
    Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping scheduler")).use { scheduler ->
      Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Database lock observer")).use { observer ->
        database.startHousekeeping(scheduler, Executor { it.run() }).use {
          val completed = CountDownLatch(1)
          database.registerHousekeeper(Housekeeper {
            observer.submit { database.mapNames() }.get(10, TimeUnit.SECONDS)
            completed.countDown()
            TEST_DELAY
          })
          assertTrue(completed.await(10, TimeUnit.SECONDS), "Housekeeping must run without the database lock")
        }
      }
    }
    database.close()
  }

  @Test
  fun `database close waits for housekeeping before closing blocks`() {
    val blocksDatabase = TrackingBlocksDatabase()
    val database = DurableDatabaseImpl(blocksDatabase)
    val housekeeper = BlockingHousekeeper()

    Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
      Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping scheduler")).use { scheduler ->
        database.startHousekeeping(scheduler, executor)
        database.registerHousekeeper(housekeeper)
        assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "Housekeeping must start before database close")

        val close = executor.submit { database.close() }
        assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "Database close must cancel housekeeping")
        assertFalse(blocksDatabase.closed, "The block database must stay open while housekeeping stops")

        housekeeper.release.countDown()
        close.get(10, TimeUnit.SECONDS)
        assertTrue(blocksDatabase.closed, "The block database must close after housekeeping stops")
      }
    }
  }

  @Test
  fun `database close reports a block close failure after housekeeping stops`() {
    val blocksFailure = IOException("Expected block database close failure")
    val blocksDatabase = TrackingBlocksDatabase(blocksFailure)
    val database = DurableDatabaseImpl(blocksDatabase)
    val housekeeper = BlockingHousekeeper()

    Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Housekeeping test worker")).use { executor ->
      Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Housekeeping scheduler")).use { scheduler ->
        database.startHousekeeping(scheduler, executor)
        database.registerHousekeeper(housekeeper)
        assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "Housekeeping must start before database close")

        val close = executor.submit<IOException?> {
          try {
            database.close()
            null
          }
          catch (failure: IOException) {
            failure
          }
        }
        assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "Database close must cancel housekeeping")
        housekeeper.release.countDown()

        assertSame(blocksFailure, close.get(10, TimeUnit.SECONDS), "Database close must report the block close failure")
        assertTrue(blocksDatabase.closed, "The block database must close after housekeeping stops")
      }
    }
  }

  @Test
  fun `store installation handle cancels and removes its housekeeper`(@TempDir directory: Path) {
    DatabaseHousekeepingCoordinator().use { coordinator ->
      BlocksDatabaseImpl.open(directory, 1024 * 1024, true, false).use { blocksDatabase ->
        val store = DurableDatabaseImpl.HousekeepingBlocksStore(blocksDatabase.openStore("store", 1), coordinator)
        val housekeeper = BlockingHousekeeper()
        val installation = store.installHousekeeper(housekeeper)

        Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("Store housekeeper test worker")).use { executor ->
          val run = executor.submit { coordinator.runHousekeeping() }
          assertTrue(housekeeper.started.await(10, TimeUnit.SECONDS), "Housekeeping must start before the installation closes")

          val closeInstallation = executor.submit { installation.close() }
          assertTrue(housekeeper.cancelled.await(10, TimeUnit.SECONDS), "The installation close must cancel its housekeeping run")
          assertFalse(closeInstallation.isDone, "The installation close must wait for its housekeeping run")

          housekeeper.release.countDown()
          run.get(10, TimeUnit.SECONDS)
          closeInstallation.get(10, TimeUnit.SECONDS)
        }

        installation.close()
        coordinator.runHousekeeping()
        assertThrows(IllegalStateException::class.java, { store.installHousekeeper(Housekeeper { TEST_DELAY }) },
                     "A closed installation must reject a new housekeeper")
      }
    }
  }

  @Test
  fun `dead block sweep retires only unreachable sealed data blocks`(@TempDir directory: Path) {
    val firstValue = "a".repeat(70 * 1024)
    val liveValue = "b".repeat(70 * 1024)
    val replacementValue = "c".repeat(70 * 1024)
    BlocksDatabaseFactory(1024 * 1024).open(directory).use { blocksDatabase ->
      DurableDatabaseImpl(blocksDatabase).use { database ->
        val descriptor = stringAsUTF8()
        val map = database.openMap("map", 1, descriptor, descriptor)
        map.put("updated", firstValue)
        map.put("live", liveValue)
        map.put("updated", replacementValue)

        val store = requireNotNull(blocksDatabase.findStore("map"))
        val dataBlocks = store.blocks().filter { it.role() == DATA.persistentCode() }
        assertEquals(listOf(SEALED, SEALED, ACTIVE), dataBlocks.map { it.state() })

        val housekeeper = RetireUnusedBlockInDurableMapHousekeeper(map as DurableMapOverBlocks<*, *>)
        housekeeper.runHousekeeping { false }
        housekeeper.runHousekeeping { false }

        assertEquals(listOf(RETIRED, SEALED, ACTIVE), dataBlocks.map { it.state() })
        assertEquals(liveValue, map.get("live"))
        assertEquals(replacementValue, map.get("updated"))
      }
    }

    BlocksDatabaseFactory(1024 * 1024).open(directory).use { blocksDatabase ->
      DurableDatabaseImpl(blocksDatabase).use { database ->
        val descriptor = stringAsUTF8()
        val map = database.openMap("map", 1, descriptor, descriptor)
        assertEquals(liveValue, map.get("live"))
        assertEquals(replacementValue, map.get("updated"))
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

  private class TrackingBlocksDatabase(private val closeFailure: IOException? = null) : BlocksDatabase {
    @Volatile
    var closed = false
      private set

    override fun openStore(name: String, dataVersion: Int): BlocksStore = error("The test must not open a store")

    override fun findStore(name: String): BlocksStore? = null

    override fun storeNames(): List<String> = emptyList()

    override fun isDirty(): Boolean = false

    override fun flush() = Unit

    override fun isClosed(): Boolean = closed

    override fun close() {
      closed = true
      closeFailure?.let { throw it }
    }
  }
}
