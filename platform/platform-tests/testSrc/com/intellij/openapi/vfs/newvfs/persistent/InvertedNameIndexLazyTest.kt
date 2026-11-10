// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.InvertedNameIndexLazy
import com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID
import com.intellij.testFramework.junit5.TestApplication
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntLists
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
class InvertedNameIndexLazyTest {
  @Test
  fun `a canceled caller stops waiting for a fill that does not complete`() {
    val lazy = InvertedNameIndexLazy(CompletableFuture())
    val indicator = EmptyProgressIndicator()
    assertTimeoutPreemptively(Duration.ofSeconds(10)) {
      assertThrows<ProcessCanceledException> {
        ProgressManager.getInstance().runProcess(
          {
            indicator.cancel()
            lazy.getWithCheckCanceled()
          },
          indicator,
        )
      }
    }
  }

  @Test
  fun `a completed fill returns the same index for both waits`() {
    val index = DefaultInMemoryInvertedNameIndex()
    val lazy = InvertedNameIndexLazy(CompletableFuture.completedFuture(index))
    assertSame(index, lazy.getWithCheckCanceled())
    assertSame(index, lazy.get())
  }

  @Test
  fun `a fill that fails during the wait reports the failure as for the blocking wait`() {
    val fillTask = object : CompletableFuture<InvertedNameIndex>() {
      override fun get(timeout: Long, unit: TimeUnit): InvertedNameIndex {
        completeExceptionally(IOException("records storage is broken"))
        return super.get(timeout, unit)
      }
    }
    val lazy = InvertedNameIndexLazy(fillTask)
    assertThrowsExactly(IllegalStateException::class.java) { lazy.getWithCheckCanceled() }
    assertThrowsExactly(IllegalStateException::class.java) { lazy.get() }
  }

  @Test
  fun `a fill that is canceled during the wait reports the failure as for the blocking wait`() {
    val fillTask = object : CompletableFuture<InvertedNameIndex>() {
      override fun get(timeout: Long, unit: TimeUnit): InvertedNameIndex {
        completeExceptionally(CancellationException("VFS scope is canceled"))
        return super.get(timeout, unit)
      }
    }
    val lazy = InvertedNameIndexLazy(fillTask)
    assertThrowsExactly(IllegalStateException::class.java) { lazy.getWithCheckCanceled() }
  }

  @Test
  fun `a fill on demand skips a name change before the start and reads it from the storage`() {
    val storage = Int2IntOpenHashMap()
    val lazy = InvertedNameIndexLazy({ indexOf(storage) }, /*fillOnDemand = */ true)

    storage.put(1, 10)
    lazy.updateFileName(1, NULL_NAME_ID, 10)
    storage.put(1, 20)
    lazy.updateFileName(1, 10, 20)

    val index = lazy.getWithCheckCanceled()
    assertEquals(emptyList<Int>(), index.fileIds(10))
    assertEquals(listOf(1), index.fileIds(20))
    index.checkConsistency()
  }

  @Test
  fun `a name change during the fill is applied once after the scan`() {
    val scanStarted = CountDownLatch(1)
    val scanMayFinish = CountDownLatch(1)
    val lazy = InvertedNameIndexLazy({
      val index = DefaultInMemoryInvertedNameIndex()
      // the scan reads file 1 after its change, and file 2 before its change
      index.updateFileName(1, NULL_NAME_ID, 20)
      index.updateFileName(2, NULL_NAME_ID, 10)
      scanStarted.countDown()
      scanMayFinish.await(10, TimeUnit.SECONDS)
      index
    }, /*fillOnDemand = */ true)

    val reader = CompletableFuture.supplyAsync { lazy.get() }
    scanStarted.await(10, TimeUnit.SECONDS)
    lazy.updateFileName(1, 10, 20)
    lazy.updateFileName(2, 10, 20)
    scanMayFinish.countDown()

    val index = reader.get(10, TimeUnit.SECONDS)
    assertEquals(emptyList<Int>(), index.fileIds(10))
    assertEquals(listOf(1, 2), index.fileIds(20))
    index.checkConsistency()

    lazy.updateFileName(2, 20, 30)
    assertEquals(listOf(1), index.fileIds(20))
    assertEquals(listOf(2), index.fileIds(30))
  }

  private fun indexOf(storage: Int2IntOpenHashMap): InvertedNameIndex {
    val index = DefaultInMemoryInvertedNameIndex()
    for (entry in storage.int2IntEntrySet()) {
      index.updateFileName(entry.intKey, NULL_NAME_ID, entry.intValue)
    }
    return index
  }

  private fun InvertedNameIndex.fileIds(nameId: Int): List<Int> {
    val fileIds = ArrayList<Int>()
    forEachFileIds(IntLists.singleton(nameId)) { fileIds.add(it) }
    return fileIds.sorted()
  }
}
