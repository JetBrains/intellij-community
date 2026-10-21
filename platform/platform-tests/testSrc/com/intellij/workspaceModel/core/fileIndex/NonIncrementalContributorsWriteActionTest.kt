// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class NonIncrementalContributorsWriteActionTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val disposable get() = projectModel.disposableRule.disposable

  @Test
  fun `write action interrupts a slow exclude policy`() {
    timeoutRunBlocking(1.minutes) {
      lateinit var excludedDir: VirtualFile
      val policy = BlockingExcludePolicy()
      lateinit var index: WorkspaceFileIndexEx
      edtWriteAction {
        val root = projectModel.baseProjectDir.newVirtualDirectory("root")
        excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/excluded")
        ModuleRootModificationUtil.addContentRoot(projectModel.createModule(), root)
        policy.excludedUrl = excludedDir.url
        DirectoryIndexExcludePolicy.EP_NAME.getPoint(projectModel.project).registerExtension(policy, disposable)
        index = WorkspaceFileIndexEx.getInstance(projectModel.project)
        index.indexData.resetCustomContributors()
      }

      val result = AtomicReference<WorkspaceFileInternalInfo>()
      val failure = AtomicReference<Throwable>()
      val reader = Thread {
        try {
          result.set(index.getFileInfo(excludedDir, true, true, true, true, true, true, true))
        }
        catch (t: Throwable) {
          failure.set(t)
        }
      }
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
      reader.join(TimeUnit.SECONDS.toMillis(BLOCK_SECONDS))
      assertFalse(reader.isAlive, "The reader did not finish the restarted rebuild")
      val error = failure.get()
      if (error != null) throw error
      assertEquals(WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED, result.get())
    }
  }

  /**
   * Blocks the first call from [readerThread] for at most [BLOCK_SECONDS], so the unfixed code fails instead of hanging.
   */
  private class BlockingExcludePolicy : DirectoryIndexExcludePolicy {
    @Volatile
    var excludedUrl: String? = null
    @Volatile
    var readerThread: Thread? = null
    val entered = CountDownLatch(1)
    val released = CountDownLatch(1)
    val canceled = AtomicBoolean()
    private val blocked = AtomicBoolean()

    override fun getExcludeUrlsForProject(): Array<String> {
      if (Thread.currentThread() === readerThread && blocked.compareAndSet(false, true)) {
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
