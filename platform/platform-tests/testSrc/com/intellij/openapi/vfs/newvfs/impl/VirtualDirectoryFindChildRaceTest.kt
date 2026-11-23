// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.isDirectory

/**
 * A refresh-and-find of a new child races with [VirtualDirectoryImpl.getChildren] of the same directory.
 * The test holds the `directoryData` lock to force the order of the two threads.
 */
@TestApplication
class VirtualDirectoryFindChildRaceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  @Suppress("SuspiciousPackagePrivateAccess") // directoryData is the lock of the race
  fun `refreshAndFind finds a new child while getChildren stores a stale listing`() {
    val dirPath = tempDir.resolve("project").createDirectory()
    val dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dirPath) as VirtualDirectoryImpl
    // A lookup of a missing child sets the case sensitivity of the directory, as the project open does.
    // Else the lookup below waits for the lock in the case sensitivity update, before the first allChildrenLoaded() check.
    assertNull(dir.findChild(".gitignore"))
    assertFalse(dir.childrenCaseSensitivity.isUnknown)
    assertFalse(dir.allChildrenLoaded())

    // getChildren() lists the disk outside the directoryData lock. The child does not exist yet.
    assertTrue((PersistentFS.getInstance() as PersistentFSImpl).listAllChildren(dir).children().isEmpty())
    val srcPath = dirPath.resolve("src").createDirectory()

    val lookup = CompletableFuture<VirtualFile?>()
    val lookupThread = Thread({
      try {
        lookup.complete(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(srcPath))
      }
      catch (e: Throwable) {
        lookup.completeExceptionally(e)
      }
    }, "lookup of src")
    synchronized(dir.directoryData) {
      lookupThread.start()
      awaitBlockedInFindInPersistence(lookupThread)

      // getChildren() takes the lock again and stores the stale listing as the complete children list.
      assertEquals(0, dir.children.size)
      assertTrue(dir.allChildrenLoaded())
    }

    assertTrue(srcPath.isDirectory())
    val src = lookup.get(1, TimeUnit.MINUTES)
    assertNotNull(src, "refreshAndFindFileByNioFile returned null for the existing directory $srcPath")
    assertTrue(Files.isSameFile(srcPath, src!!.toNioPath()))
  }

  private fun awaitBlockedInFindInPersistence(thread: Thread) {
    val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
    while (!isBlockedInFindInPersistence(thread)) {
      check(thread.isAlive && System.nanoTime() < deadline) {
        "The lookup did not wait for the directoryData lock in findInPersistence: ${thread.stackTrace.contentToString()}"
      }
      Thread.sleep(1)
    }
  }

  private fun isBlockedInFindInPersistence(thread: Thread): Boolean {
    return thread.state == Thread.State.BLOCKED && thread.stackTrace.any { it.methodName == "findInPersistence" }
  }
}
