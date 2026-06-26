// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.async

import com.intellij.mock.MockLocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncFileContentWriteRequestor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class AsyncableFileSystemWrapperTest {

  private val asyncAllowingRequestor = object: AsyncFileContentWriteRequestor{}
  private val asyncDisallowingRequestor = Any()

  private lateinit var underlyingFileSystem: TestFileSystem
  private lateinit var asyncFileSystemWrapper: AsyncableFileSystemWrapper
  private lateinit var file: TestVirtualFile

  @BeforeEach
  fun setUp() {
    underlyingFileSystem = TestFileSystem()
    asyncFileSystemWrapper = AsyncableFileSystemWrapper(underlyingFileSystem)
    file = TestVirtualFile(underlyingFileSystem, "/file.txt", 1)
  }

  @AfterEach
  fun tearDown() {
    underlyingFileSystem.unblockWrites()
    if (::asyncFileSystemWrapper.isInitialized) {
      runCatching { asyncFileSystemWrapper.close() }
    }
  }

  @Test
  fun `pending write is visible before fsync`() {
    assumeTrue(ASYNC_CONTENT_WRITE_ENABLED){ "Test implies async writes are enabled" }
    
    underlyingFileSystem.blockWrites()
    val content = "new content".toByteArray()

    asyncFileSystemWrapper.getOutputStream(file, asyncAllowingRequestor, 1, -1).use { it.write(content) }

    assertTrue(underlyingFileSystem.awaitWriteStarted())
    assertTrue(asyncFileSystemWrapper.hasUnfinishedTasksFor(file))
    assertContentEquals(content, asyncFileSystemWrapper.contentsToByteArray(file))
    assertEquals(content.size.toLong(), asyncFileSystemWrapper.getLength(file))

    val pendingTimestamp = asyncFileSystemWrapper.getTimeStamp(file)
    assertTrue(pendingTimestamp > 0)
    assertContentEquals(ByteArray(0), underlyingFileSystem.contentsToByteArray(file))

    underlyingFileSystem.unblockWrites()
    asyncFileSystemWrapper.fsync(file)

    assertFalse(asyncFileSystemWrapper.hasUnfinishedTasks())
    assertContentEquals(content, underlyingFileSystem.contentsToByteArray(file))
    assertEquals(pendingTimestamp, underlyingFileSystem.getTimeStamp(file))
  }

  @Test
  fun `rename waits for pending write`() {
    assumeTrue(ASYNC_CONTENT_WRITE_ENABLED){ "Test implies async writes are enabled" }

    underlyingFileSystem.blockWrites()
    asyncFileSystemWrapper.getOutputStream(file, asyncAllowingRequestor, 1, -1).use { it.write(byteArrayOf(1, 2, 3)) }

    assertTrue(underlyingFileSystem.awaitWriteStarted())

    val renameFuture = CompletableFuture.runAsync(
      { asyncFileSystemWrapper.renameFile(asyncAllowingRequestor, file, "renamed.txt") },
      AppExecutorUtil.getAppExecutorService(),
    )

    Thread.sleep(100)
    assertFalse(renameFuture.isDone)
    assertEquals(0, underlyingFileSystem.renameCalls.get())

    underlyingFileSystem.unblockWrites()
    renameFuture.get(5, SECONDS)

    assertEquals(1, underlyingFileSystem.renameCalls.get())
  }

  @Test
  fun `async disallowing requestor stays synchronous`() {
    underlyingFileSystem.blockWrites()
    val content = "sync".toByteArray()

    val closeFuture = CompletableFuture.runAsync(
      { asyncFileSystemWrapper.getOutputStream(file, asyncDisallowingRequestor, 1, -1).use { it.write(content) } },
      AppExecutorUtil.getAppExecutorService(),
    )

    assertTrue(underlyingFileSystem.awaitWriteStarted())
    Thread.sleep(100)
    assertFalse(closeFuture.isDone)

    underlyingFileSystem.unblockWrites()
    closeFuture.get(5, SECONDS)

    assertFalse(asyncFileSystemWrapper.hasUnfinishedTasks())
    assertFalse(asyncFileSystemWrapper.hasUnfinishedTasksFor(file))
    assertContentEquals(content, underlyingFileSystem.contentsToByteArray(file))
  }

  @Test
  fun `fsync reports async write failures`() {
    underlyingFileSystem.failWrites = true


    assertThrows<IOException> {
      asyncFileSystemWrapper.getOutputStream(file, asyncAllowingRequestor, 1, -1).use { it.write(byteArrayOf(1)) }
      asyncFileSystemWrapper.fsync(file)
    }
  }

  @Test
  fun `implicit timestamp update failures are swallowed`() {
    underlyingFileSystem.failTimeStampUpdates = true
    underlyingFileSystem.timeStampAfterImplicitWrite = 42L
    val content = "content".toByteArray()

    asyncFileSystemWrapper.getOutputStream(file, asyncAllowingRequestor, 1, -1).use { it.write(content) }
    asyncFileSystemWrapper.fsync(file)

    assertFalse(asyncFileSystemWrapper.hasUnfinishedTasks())
    assertContentEquals(content, underlyingFileSystem.contentsToByteArray(file))
    assertEquals(42L, asyncFileSystemWrapper.getTimeStamp(file))
  }

  @Test
  fun `explicit timestamp update failures are reported`() {
    underlyingFileSystem.failTimeStampUpdates = true

    assertThrows<IOException> {
      asyncFileSystemWrapper.getOutputStream(file, asyncAllowingRequestor, 1, 42L).use { it.write(byteArrayOf(1)) }
      asyncFileSystemWrapper.fsync(file)
    }
  }

  private class TestFileSystem : MockLocalFileSystem() {
    private val contentByPath = HashMap<String, ByteArray>()
    private val timeStampByPath = HashMap<String, Long>()

    val renameCalls = AtomicInteger()

    @Volatile
    var failWrites = false

    @Volatile
    var failTimeStampUpdates = false

    @Volatile
    var timeStampAfterImplicitWrite = -1L

    @Volatile
    private var releaseWrites = CountDownLatch(0)

    @Volatile
    private var writeStarted = CountDownLatch(0)

    fun blockWrites() {
      releaseWrites = CountDownLatch(1)
      writeStarted = CountDownLatch(1)
    }

    fun unblockWrites() {
      releaseWrites.countDown()
    }

    fun awaitWriteStarted(): Boolean = writeStarted.await(5, SECONDS)

    override fun contentsToByteArray(file: VirtualFile): ByteArray {
      return contentByPath[file.path]?.copyOf() ?: ByteArray(0)
    }

    override fun getInputStream(file: VirtualFile) = ByteArrayInputStream(contentsToByteArray(file))

    override fun getLength(file: VirtualFile): Long = contentByPath[file.path]?.size?.toLong() ?: 0

    override fun getTimeStamp(file: VirtualFile): Long = timeStampByPath[file.path] ?: 0

    override fun getOutputStream(file: VirtualFile, requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
      return object : ByteArrayOutputStream() {
        override fun close() {
          super.close()
          writeStarted.countDown()
          if (!releaseWrites.await(5, SECONDS)) {
            throw IOException("Timed out waiting for the test to unblock writes")
          }
          if (failWrites) {
            throw IOException("Write failed")
          }
          contentByPath[file.path] = toByteArray()
          timeStampByPath[file.path] = if (timeStamp > 0) timeStamp else timeStampAfterImplicitWrite
        }
      }
    }

    override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
      if (failTimeStampUpdates) {
        throw IOException("Timestamp update failed")
      }
      timeStampByPath[file.path] = timeStamp
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
      renameCalls.incrementAndGet()
    }
  }

  private class TestVirtualFile(
    fileSystem: VirtualFileSystem,
    private val filePath: String,
    private val id: Int,
  ) : StubVirtualFile(fileSystem), VirtualFileWithId {
    override fun getPath(): String = filePath

    override fun getName(): String = filePath.substringAfterLast('/')

    override fun getParent(): VirtualFile? = null

    override fun getUrl(): String = "${fileSystem.protocol}://$filePath"

    override fun isDirectory(): Boolean = false

    override fun isValid(): Boolean = true

    override fun isWritable(): Boolean = true

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    }

    override fun setWritable(writable: Boolean) {
    }

    override fun getId(): Int = id
  }
}
