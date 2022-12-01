// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.impl.IndexDebugProperties
import com.intellij.util.io.IOUtil.MiB
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ResizableMappedFileTest {
  companion object {
    @JvmStatic
    @BeforeClass
    fun `initialize index debug for local run`() {
      IndexDebugProperties.DEBUG = true
    }
  }

  @get: Rule
  val tempDir = TempDirectory()

  @get: Rule
  val disposable = DisposableRule()

  @Test
  fun `put data to non-existing page`() {
    val storagePath = tempDir.newDirectoryPath().resolve("non-existing-page-test")

    val address = 5L * MiB + 123
    val value = 112233L

    ResizeableMappedFile(
      storagePath,
      MiB,
      null,
      MiB,
      true
    ).write {
      use { file ->
        file.putLong(address, value)
        Assert.assertEquals(value, file.getLong(address))

        file.force()

        Assert.assertEquals(value, file.getLong(address))

        file.force()
        StorageLockContext.forceDirectMemoryCache()

        Assert.assertEquals(value, file.getLong(address))
      }
    }

    ResizeableMappedFile(
      storagePath,
      MiB,
      null,
      MiB,
      true
    ).write {
      use { file ->
        Assert.assertEquals(value, file.getLong(address))

        Assert.assertEquals(address + 8, file.length())
      }
    }

    Assert.assertTrue(address + 8 < storagePath.size())
  }

  @Test
  fun testCacheMisses() {
    val fileCount = (StorageLockContext.getCacheMaxSize() / MiB + 10).toInt()
    val pageSize = MiB
    Assert.assertTrue(fileCount * pageSize > StorageLockContext.getCacheMaxSize())

    val directory = tempDir.newDirectory("resizable-mapped-files").toPath()
    val resizableMappedFiles = (0..fileCount).map {
      val file = ResizeableMappedFile(
        directory.resolve("map$it"),
        MiB,
        null,
        MiB,
        true
      )

      Disposer.register(disposable.disposable, Disposable {
        try {
          file.close()
        }
        catch (_: Exception) {

        }
      })

      file
    }

    StorageLockContext.forceDirectMemoryCache()
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked()

    // fill the cache
    for (i in 0..fileCount) {
      if (i % 100 == 0) {
        println("$i of $fileCount")
      }
      resizableMappedFiles[i].write { putInt(0L, 239) }
    }

    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked()
    var stats = StorageLockContext.getStatistics()

    for (i in 0..fileCount) {
      if (i % 100 == 0) {
        println("$i of $fileCount")
      }
      resizableMappedFiles[i].write { putInt(0L, 239) }

      val statsAfterOp = StorageLockContext.getStatistics()

      val pageLoadDiff = statsAfterOp.pageLoad - stats.pageLoad
      val pageMissDiff = statsAfterOp.pageMiss - stats.pageMiss
      val pageHitDiff = statsAfterOp.pageHit - stats.pageHit
      val pageFastCacheHit = statsAfterOp.pageFastCacheHit - stats.pageFastCacheHit

      Assert.assertEquals(0, pageLoadDiff)
      Assert.assertEquals(1, pageMissDiff)
      Assert.assertEquals(0, pageHitDiff)
      Assert.assertEquals(0, pageFastCacheHit)

      stats = statsAfterOp
    }
  }

  private fun ResizeableMappedFile.write(op: ResizeableMappedFile.() -> Unit) {
    lockWrite()
    try {
      op()
    }
    finally {
      unlockWrite()
    }
  }
}