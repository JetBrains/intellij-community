// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResizableMappedFileTest {
  companion object {
    @JvmStatic
    @BeforeClass
    fun `initialize index debug for local run`() {
      IndexDebugProperties.DEBUG = true
    }
  }

  @get: Rule
  val tempDir: TempDirectory = TempDirectory()

  @get: Rule
  val disposable: DisposableRule = DisposableRule()

  @Test
  fun `put data to non-existing page`() {
    val storagePath = tempDir.newDirectoryPath().resolve("non-existing-page-test")

    val address = 5L * MiB + 123
    val value = 112233L

    ResizeableMappedFile(storagePath, MiB, null, MiB, true).useForWrite { file ->
      file.putLong(address, value)
      assertEquals(value, file.getLong(address))

      file.force()

      assertEquals(value, file.getLong(address))

      file.force()
      StorageLockContext.forceDirectMemoryCache()

      assertEquals(value, file.getLong(address))
    }

    ResizeableMappedFile(storagePath, MiB, null, MiB, true).useForWrite { file ->
      assertEquals(value, file.getLong(address))

      assertEquals(address + 8, file.length())
    }

    assertTrue(address + 8 < storagePath.fileSize())
  }

  @Test
  fun `RMF successfully opens in non-existing directory`() {
    val nonExistingDir = tempDir.newDirectoryPath().resolve("non-existing-dir")
    assertFalse("Must not exist") { nonExistingDir.exists() }

    val pathInNonExistingDir = nonExistingDir.resolve("test-file")

    ResizeableMappedFile(pathInNonExistingDir, MiB, null, MiB, true).useForWrite { rmf ->
      assertEquals(0, rmf.length(), "Just-created rmf must have length=0")

      //check: file could be written into, without exceptions
      val offset = 10L
      val value = 42L
      rmf.putLong(offset, value)
      rmf.force()

      assertTrue("Now parent dir must be created") { nonExistingDir.exists() }
      assertTrue("Now file must be created") { pathInNonExistingDir.exists() }
    }
  }

  @Test
  fun `RMF successfully re-creates missed length file (but length itself is not recovered)`() {
    val storagePath = tempDir.newDirectoryPath().resolve("test-file")
    val lengthPath = deriveLengthFile(storagePath)

    val offset = 10L
    val valueWritten = 42L

    ResizeableMappedFile(storagePath, 0, null, MiB, true).useForWrite { rmf ->
      //check: file could be written into, without exceptions
      rmf.putLong(offset, valueWritten)
      rmf.force()

      assertTrue("Now file must be created") { storagePath.exists() }
      assertTrue("Now .len file must be created") { lengthPath.exists() }
    }

    lengthPath.delete()

    ResizeableMappedFile(storagePath, 0, null, MiB, true).useForWrite { rmfReopened ->
      //Storage file is expanded in advance, with DEFAULT_ALLOCATION_ROUND_FACTOR, which makes it almost
      // always bigger than size of data written. This is .len-file is for: keeping 'logical size' of the
      // file, i.e. size of actual data written. With removing .len-file this info is lost, and the only
      // thing RMF could do is set it's logicalSize=physicalSize:
      assertEquals(Files.size(storagePath),
                   rmfReopened.length(),
                   ".length() must be set to size of main storage file (could be > .length() previously)"
      )
      assertEquals(valueWritten,
                   rmfReopened.getLong(offset),
                   "Value written must be preserved at offset 10")

      assertTrue(".len file must be re-created") { lengthPath.exists() }
    }
  }


  @Test
  fun `RMF successfully re-creates length file if main file is missed`() {
    val storagePath = tempDir.newDirectoryPath().resolve("test-file")
    val lengthPath = deriveLengthFile(storagePath)

    val offset = 10L
    val valueWritten = 42L

    ResizeableMappedFile(storagePath, 0, null, MiB, true).useForWrite { rmf ->
      //check: file could be written into, without exceptions
      rmf.putLong(offset, valueWritten)
      rmf.force()

      assertTrue("Now file must be created") { storagePath.exists() }
      assertTrue("Now .len file must be created") { lengthPath.exists() }
    }

    storagePath.delete()

    ResizeableMappedFile(storagePath, 0, null, MiB, true).useForWrite { rmfReopened ->
      //data is lost, obviously, but ensure at least there is no mess with logical size -- logical size
      // must be 0 since main file is void:
      assertEquals(0,
                   rmfReopened.length(),
                   ".length() must be 0 since main file was removed (=effectively empty)"
      )
      assertTrue(".len file must be re-created") { lengthPath.exists() }
    }
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

      val pageLoadDiff = statsAfterOp.regularPageLoads - stats.regularPageLoads
      val pageMissDiff = statsAfterOp.pageLoadsAboveSizeThreshold - stats.pageLoadsAboveSizeThreshold
      val pageHitDiff = statsAfterOp.pageHits - stats.pageHits
      val pageFastCacheHit = statsAfterOp.pageFastCacheHits - stats.pageFastCacheHits

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

  private fun <A> ResizeableMappedFile.useForWrite(op: (ResizeableMappedFile) -> A): A {
    lockWrite()
    try {
      return use(op)
    }
    finally {
      unlockWrite()
    }
  }

  //RMF.getLengthFile() is private:
  private fun deriveLengthFile(storagePath: Path): Path =
    storagePath.resolveSibling(storagePath.fileName.toString() + ".len")
}