// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.InvertedNameIndexLazy
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
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
}
