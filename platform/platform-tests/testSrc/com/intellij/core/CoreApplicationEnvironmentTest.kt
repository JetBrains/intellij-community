// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class CoreApplicationEnvironmentTest {
  @Test
  fun `test initialization`() {
    Disposer.newDisposable().use {
      CoreApplicationEnvironment(it)
    }
  }

  @Test
  fun `an environment without the headless mode keeps the shared thread pool`() {
    Disposer.newDisposable().use {
      CoreApplicationEnvironment(it)
      val thread = AppExecutorUtil.getAppExecutorService().submit(Callable { Thread.currentThread() }).get(10, TimeUnit.SECONDS)
      assertFalse("A pooled thread is daemon: ${thread.name}", thread.isDaemon)
    }
  }
}
