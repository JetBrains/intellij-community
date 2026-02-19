// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy
import com.intellij.util.io.pagecache.impl.PageImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException


private const val PAGE_SIZE = 8192

class PagedFileStorageWithRWLockedPageContent_Livelock_Test {

  private var filePageCache: FilePageCacheLockFree? = null
  private var storageContext: StorageLockContext? = null

  @BeforeEach
  fun setup() {
    //just 1 page
    filePageCache = FilePageCacheLockFree(PAGE_SIZE.toLong())
    storageContext = StorageLockContext(filePageCache, true, true, false)
  }

  @AfterEach
  fun tearDown() {
    filePageCache?.close()
  }

  // ====================== tests:     ==============================================================

  @Test //IDEA-323586
  fun pageAcquiredByOtherInABOUT_TO_UNMAPStateCouldStillBeAcquired(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test.dat")
    openFile(file).use { storage ->
      storage.pageByIndex(0, true).use { page0 ->
        //The scenario we're testing (IDEA-323586) is: the page was moved to ABOUT_TO_UNMAP, but concurrently
        // client_1 acquired the page, so (page.inUse > 0) -- and client_1 continues to use the page.
        // Another client_2 tries to acquire the page, but gets stuck in a spinloop, because page in ABOUT_TO_UNMAP
        // can't be acquired -- and client_1 doesn't release the page for it to be reclaimed and re-allocated
        // again.
        // It is hard to pinpoint the exact scenario, since it relies on a narrow time window, so we setup it
        // explicitly: make currently used page ABOUT_TO_UNMAP, and try to acquire it again.
        (page0 as PageImpl).tryMoveTowardsPreTombstone(false)
        storage.pageByIndex(0, true).use { page0_1 -> }
      }
    }
  }

  @Test //IDEA-323586
  fun pageAcquiredByOtherInABOUT_TO_UNMAPStateCouldStillBeAcquired_Real(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test2.dat")
    val executor = Executors.newFixedThreadPool(2)
    try {
      //IDEA-323586: Same scenario as above: the page was moved to ABOUT_TO_UNMAP, but concurrently
      // client_1 acquired the page, so (page.inUse>0) -- and client_1 continues to use the page for a long.
      // Another client_2 tries to acquire the page, but gets stuck in a spinloop, because ABOUT_TO_UNMAP
      // can't be acquired -- and client_1 doesn't release the page for it to be reclaimed and re-allocated
      // again.
      // Here we try to pinpoint 'real-life' scenario, without cheating:

      openFile(file).use { storage ->

        val future = executor.submit {
          while (!Thread.interrupted()) {
            for (pageNo in 0..9) {
              storage.pageByIndex(pageNo, true).use { page -> Thread.yield() }
            }
          }
        }

        executor.submit {
          while (!Thread.interrupted()) {
            for (pageNo in 0..9) {
              storage.pageByIndex(pageNo, true).use { page -> Thread.yield() }
            }
          }
        }
        future.get(20, SECONDS)
      }
    }
    catch (e: TimeoutException) {
      //ok
    }
    finally {
      executor.shutdown()
      executor.awaitTermination(10, SECONDS)
    }
  }

  // ====================== infrastructure:  ==============================================================

  private fun openFile(file: Path): PagedFileStorageWithRWLockedPageContent {
    return PagedFileStorageWithRWLockedPageContent(
      file,
      storageContext!!,
      PAGE_SIZE,
      true,
      false,
      PageContentLockingStrategy.LOCK_PER_PAGE
    )
  }

}