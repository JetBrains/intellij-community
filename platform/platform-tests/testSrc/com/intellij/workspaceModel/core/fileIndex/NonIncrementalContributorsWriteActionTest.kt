// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class NonIncrementalContributorsWriteActionTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val disposable get() = projectModel.disposableRule.disposable

  @Test
  fun `write action interrupts a slow exclude policy`() {
    timeoutRunBlocking(1.minutes) {
      val policy = BlockingExcludePolicy()
      val excludedDir = registerExcludedDir(policy)
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)

      val reader = FileInfoReader(index, excludedDir, insideReadAction = false)
      policy.readerThread = reader
      try {
        reader.start()
        assertTrue(policy.entered.await(BLOCK_SECONDS, TimeUnit.SECONDS), "The reader did not reach the exclude policy")
        edtWriteAction {}
        assertTrue(policy.canceled.get(), "The write action did not interrupt the read action of the rebuild")
      }
      finally {
        policy.released.countDown()
      }
      assertFinishedExcluded(reader)
    }
  }

  @Test
  fun `write action interrupts a thread that waits for the rebuild`() {
    doTestWaiter(waiterInsideReadAction = false)
  }

  @Test
  fun `write action interrupts a thread that waits for the rebuild inside its own read action`() {
    doTestWaiter(waiterInsideReadAction = true)
  }

  private fun doTestWaiter(waiterInsideReadAction: Boolean) {
    timeoutRunBlocking(1.minutes) {
      val policy = BlockingExcludePolicy()
      val excludedDir = registerExcludedDir(policy)
      val index = WorkspaceFileIndexEx.getInstance(projectModel.project)

      val reader = FileInfoReader(index, excludedDir, insideReadAction = false)
      val waiter = FileInfoReader(index, excludedDir, waiterInsideReadAction)
      policy.readerThread = reader
      policy.waiterThread = waiter
      try {
        reader.start()
        assertTrue(policy.entered.await(BLOCK_SECONDS, TimeUnit.SECONDS), "The reader did not reach the exclude policy")
        waiter.start()
        awaitWaitingForRebuild(waiter)
        edtWriteAction {
          policy.writeRan = true
        }
      }
      finally {
        policy.released.countDown()
      }
      assertFalse(policy.waiterEnteredBeforeWrite.get(), "The waiting thread started the rebuild before the pending write action")
      assertFinishedExcluded(reader)
      assertFinishedExcluded(waiter)
    }
  }

  private fun assertFinishedExcluded(reader: FileInfoReader) {
    reader.join(TimeUnit.SECONDS.toMillis(BLOCK_SECONDS))
    assertFalse(reader.isAlive, "${reader.name} did not finish the restarted rebuild")
    val error = reader.failure
    if (error != null) throw error
    assertEquals(WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED, reader.result)
  }

  /**
   * Registers [policy] for a new excluded directory and resets the roots of the policies.
   *
   * Scanning and the processing of changed files query the index too, so the reset waits for them; otherwise one of them
   * may compute the roots before the reader of the test.
   */
  private suspend fun registerExcludedDir(policy: BlockingExcludePolicy): VirtualFile {
    val project = projectModel.project
    val excludedDir = edtWriteAction {
      val root = projectModel.baseProjectDir.newVirtualDirectory("root")
      val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/excluded")
      ModuleRootModificationUtil.addContentRoot(projectModel.createModule(), root)
      policy.excludedUrl = excludedDir.url
      DirectoryIndexExcludePolicy.EP_NAME.getPoint(project).registerExtension(policy, disposable)
      excludedDir
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val changedFilesCollector = (FileBasedIndex.getInstance() as FileBasedIndexImpl).changedFilesCollector
    changedFilesCollector.waitForVfsEventsExecuted(BLOCK_SECONDS, TimeUnit.SECONDS) {}
    changedFilesCollector.ensureUpToDate()
    edtWriteAction {
      WorkspaceFileIndexEx.getInstance(project).indexData.resetCustomContributors()
    }
    return excludedDir
  }

  /**
   * Waits until [thread] parks inside the rebuild of `NonIncrementalContributors` and outside the exclude policy,
   * that is, on the lock that another thread holds.
   */
  private fun awaitWaitingForRebuild(thread: Thread) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BLOCK_SECONDS)
    while (System.nanoTime() < deadline) {
      if (thread.state != Thread.State.RUNNABLE) {
        val frame = thread.stackTrace.firstOrNull { it.className.startsWith("com.intellij.workspaceModel.") }
        if (frame != null && frame.className.startsWith(NON_INCREMENTAL_CONTRIBUTORS) && frame.methodName != "updateIfNeeded") {
          return
        }
      }
      Thread.sleep(10)
    }
    throw AssertionError("${thread.name} did not wait for the rebuild: ${thread.stackTrace.joinToString("\n")}")
  }

  /**
   * Queries the index for [file]; with [insideReadAction], from inside a non-blocking read action of its own.
   */
  private class FileInfoReader(
    private val index: WorkspaceFileIndexEx,
    private val file: VirtualFile,
    private val insideReadAction: Boolean,
  ) : Thread() {
    @Volatile
    var result: WorkspaceFileInternalInfo? = null
    @Volatile
    var failure: Throwable? = null

    override fun run() {
      try {
        val query = Callable { index.getFileInfo(file, true, true, true, true, true, true, true) }
        result = if (insideReadAction) ReadAction.nonBlocking(query).executeSynchronously() else query.call()
      }
      catch (t: Throwable) {
        failure = t
      }
    }
  }

  /**
   * Blocks the first call from [readerThread] for at most [BLOCK_SECONDS], so the unfixed code fails instead of hanging.
   * Records whether [waiterThread] calls the policy before the write action runs.
   */
  private class BlockingExcludePolicy : DirectoryIndexExcludePolicy {
    @Volatile
    var excludedUrl: String? = null
    @Volatile
    var readerThread: Thread? = null
    @Volatile
    var waiterThread: Thread? = null
    @Volatile
    var writeRan: Boolean = false
    val entered = CountDownLatch(1)
    val released = CountDownLatch(1)
    val canceled = AtomicBoolean()
    val waiterEnteredBeforeWrite = AtomicBoolean()
    private val blocked = AtomicBoolean()

    override fun getExcludeUrlsForProject(): Array<String> {
      val thread = Thread.currentThread()
      if (thread === waiterThread && !writeRan) {
        waiterEnteredBeforeWrite.set(true)
      }
      if (thread === readerThread && blocked.compareAndSet(false, true)) {
        entered.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BLOCK_SECONDS)
        try {
          awaitWithCheckCanceled(ThrowableComputable {
            released.await(10, TimeUnit.MILLISECONDS) || System.nanoTime() >= deadline
          })
        }
        catch (e: ProcessCanceledException) {
          canceled.set(true)
          throw e
        }
      }
      return listOfNotNull(excludedUrl).toTypedArray()
    }
  }
}

private const val BLOCK_SECONDS = 10L
private const val NON_INCREMENTAL_CONTRIBUTORS = "com.intellij.workspaceModel.core.fileIndex.impl.NonIncrementalContributors"
