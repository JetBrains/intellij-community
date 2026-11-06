// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ThreadDumperTest {
  @Test
  fun `dumps contain platform and virtual threads`() {
    val ready = CountDownLatch(4)
    val release = CountDownLatch(1)
    val work = Runnable {
      ready.countDown()
      release.await()
    }
    val platform = Thread.ofPlatform().name("dump-platform-test").start(work)
    val virtual = Thread.ofVirtual().name("dump-virtual-test").start(work)
    val unnamed = Thread.ofVirtual().start(work)
    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("dump-executor-test").factory()).use { executor ->
      val task = executor.submit(work)
      try {
        assertTrue(ready.await(10, TimeUnit.SECONDS))
        val dumps = listOf(ThreadDumper.dumpThreadsToString(), ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), true).rawDump)
        for (dump in dumps) {
          for (name in listOf("dump-platform-test", "dump-virtual-test", "dump-executor-test")) {
            assertTrue(dump.contains("\"$name\""), dump)
          }
          assertTrue(dump.contains("CountDownLatch.await"), dump)
          assertTrue(dump.contains("#${unnamed.threadId()} \"\" virtual "), dump)
        }
      }
      finally {
        release.countDown()
        platform.join(10_000)
        virtual.join(10_000)
        unnamed.join(10_000)
        task.get(10, TimeUnit.SECONDS)
      }
    }
  }
}
