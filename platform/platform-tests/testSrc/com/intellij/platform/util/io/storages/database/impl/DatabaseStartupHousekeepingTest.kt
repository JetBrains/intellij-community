// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.spi.housekeeping.OnStartupHousekeeper
import com.intellij.util.ConcurrencyUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@Suppress("SuspiciousPackagePrivateAccess")
class DatabaseStartupHousekeepingTest {
  @Test
  fun `startup sees recovered blocks and runs in order before open returns`(@TempDir directory: Path) {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val block = database.openStore("store", 1).allocateBlock(1, 32)
      block.content().set(JAVA_INT, 0, 42)
      block.activate()
    }

    val events = mutableListOf<String>()
    lateinit var content: MemorySegment
    val hooks = listOf(
      OnStartupHousekeeper { database ->
        content = requireNotNull(database.findStore("store")).blocks().single().content()
        assertEquals(42, content.get(JAVA_INT, 0), "Startup must see the recovered blocks")
        content.set(JAVA_INT, 0, 99)
        events.add("first")
      },
      OnStartupHousekeeper { events.add("second") },
    )
    val factory = BlocksDatabaseFactory.withDefaults().withStartupHousekeepers(hooks)
      .chunkSize(CHUNK_SIZE).fsyncOnFlush(true).fsyncOnClose(true)
    assertEquals(hooks, factory.startupHousekeepers(), "Factory modifiers must preserve startup housekeeping")
    val database = factory.open(directory)
    assertEquals(listOf("first", "second"), events, "Startup must finish before open returns")
    database.close()
    assertEquals(listOf("first", "second"), events, "Closing must not run startup housekeeping again")
    assertFalse(content.scope().isAlive, "Normal close must release the mappings")

    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { reopened ->
      val block = requireNotNull(reopened.findStore("store")).blocks().single()
      assertEquals(99, block.content().get(JAVA_INT, 0), "Startup changes must survive reopening")
    }
  }

  @Test
  fun `map factory runs startup without a scheduler or repeated regular runs`(@TempDir directory: Path) {
    val events = mutableListOf<String>()
    val hooks = listOf(OnStartupHousekeeper { events.add("startup") })
    val factory = DurableDatabaseFactory.withDefaults().startupHousekeeping(*hooks.toTypedArray())
      .chunkSize(CHUNK_SIZE).fsyncOnFlush(false).fsyncOnClose(true)
    assertEquals(hooks, factory.startupHousekeepers(), "Factory modifiers must preserve startup housekeeping")
    factory.open(directory).use { database ->
      assertEquals(listOf("startup"), events, "Startup must finish before clients can open maps")
      val descriptor = stringAsUTF8()
      val map = database.openMap("map", 1, descriptor, descriptor)
      map.put("key", "value")
      assertEquals("value", map.get("key"))
    }
    assertEquals(listOf("startup"), events, "Close must not repeat startup housekeeping")
  }

  @Test
  fun `startup failure closes resources and skips later hooks`(@TempDir directory: Path) {
    val failures = listOf(IOException("Expected I/O failure"), IllegalStateException("Expected unchecked failure"),
                          AssertionError("Expected error"))
    for ((index, failure) in failures.withIndex()) {
      lateinit var database: BlocksDatabaseImpl
      lateinit var content: MemorySegment
      val events = mutableListOf<String>()
      val factory = BlocksDatabaseFactory(CHUNK_SIZE).withStartupHousekeepers(listOf(
        OnStartupHousekeeper { opened ->
          database = opened as BlocksDatabaseImpl
          val block = opened.openStore("store", 1).allocateBlock(1, 32)
          content = block.content()
          block.activate()
          throw failure
        },
        OnStartupHousekeeper { events.add("unexpected") },
      ))
      val thrown = assertThrows(failure.javaClass) { factory.open(directory.resolve(index.toString())) }
      assertSame(failure, thrown, "Opening must preserve the original failure")
      assertTrue(events.isEmpty(), "A failed startup must skip later hooks")
      assertTrue(database.isClosed)
      assertFalse(content.scope().isAlive, "A failed startup must release its mappings")
    }
  }

  @Test
  fun `reused startup hook receives each database instance`(@TempDir directory: Path) {
    val databases = mutableListOf<BlocksDatabaseImpl>()
    val hook = OnStartupHousekeeper { database ->
      database.openStore("created-on-startup", 1)
      databases.add(database as BlocksDatabaseImpl)
    }
    val factory = BlocksDatabaseFactory(CHUNK_SIZE).withStartupHousekeepers(listOf(hook))
    factory.open(directory.resolve("first")).use { first ->
      factory.open(directory.resolve("second")).use { second ->
        assertEquals(listOf(first, second), databases, "A reused hook must receive the current database")
        assertNotSame(first, second)
        assertEquals(listOf("created-on-startup"), first.storeNames())
        assertEquals(listOf("created-on-startup"), second.storeNames())
      }
    }
  }

  @Test
  fun `startup hooks run outside the database lock`(@TempDir directory: Path) {
    Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Startup lock observer")).use { observer ->
      val factory = BlocksDatabaseFactory(CHUNK_SIZE).withStartupHousekeepers(listOf(OnStartupHousekeeper { database ->
        assertEquals(emptyList<String>(), observer.submit<List<String>> { database.storeNames() }.get(10, TimeUnit.SECONDS))
      }))
      factory.open(directory).close()
    }
  }

  @Test
  fun `factories keep immutable snapshots of startup hooks`(@TempDir directory: Path) {
    val events = mutableListOf<String>()
    val hooks = mutableListOf(OnStartupHousekeeper { events.add("startup") })
    val blocksFactory = BlocksDatabaseFactory(CHUNK_SIZE, false, false, hooks)
    val mapsFactory = DurableDatabaseFactory(CHUNK_SIZE, false, false, null, hooks)
    hooks.clear()

    assertThrows(UnsupportedOperationException::class.java) { blocksFactory.startupHousekeepers().clear() }
    assertThrows(UnsupportedOperationException::class.java) { mapsFactory.startupHousekeepers().clear() }
    blocksFactory.open(directory.resolve("blocks")).use { assertEquals(listOf("startup"), events) }
    mapsFactory.open(directory.resolve("maps")).use {
      assertEquals(listOf("startup", "startup"), events, "Changing the caller's list must not change either factory")
    }
  }

  @Test
  fun `scheduler initialization failure closes the database without repeating startup`(@TempDir directory: Path) {
    val events = mutableListOf<String>()
    lateinit var blocks: BlocksDatabaseImpl
    Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Rejected housekeeping scheduler")).use { scheduler ->
      scheduler.shutdown()
      val hooks = listOf(OnStartupHousekeeper { database ->
        blocks = database as BlocksDatabaseImpl
        events.add("startup")
      })
      val factory = DurableDatabaseFactory(CHUNK_SIZE).startupHousekeeping(*hooks.toTypedArray())
        .housekeeping(scheduler) { it.run() }
      assertEquals(hooks, factory.startupHousekeepers(), "Periodic configuration must preserve startup housekeeping")
      assertThrows(RejectedExecutionException::class.java) { factory.open(directory) }
      assertTrue(blocks.isClosed, "Failed facade initialization must close the block database")
      assertEquals(listOf("startup"), events)
    }
  }

  companion object {
    private const val CHUNK_SIZE = 1024 * 1024

  }
}
