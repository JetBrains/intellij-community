// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.testFramework.LoggedErrorProcessor
import jdk.jfr.consumer.RecordingStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ActivityMonitorSamplerTest {
  @Test
  fun `failed recording creation keeps scheduled updates running`() = withStartupWarning {
    val attempts = AtomicInteger()
    val updates = CountDownLatch(3)
    ActivityMonitorSampler({ name, _ -> name }, {
      attempts.incrementAndGet()
      throw IllegalStateException("Recording is unavailable")
    }).use { sampler ->
      Executors.newSingleThreadScheduledExecutor().use { executor ->
        val future = executor.scheduleWithFixedDelay({
          assertFalse(sampler.start())
          updates.countDown()
        }, 0, 1, TimeUnit.MILLISECONDS)
        try {
          assertTrue(updates.await(10, TimeUnit.SECONDS))
          assertEquals(1, attempts.get())
        }
        finally {
          future.cancel(false)
        }
      }
    }
  }

  @Test
  fun `failed recording startup closes the recording`() = withStartupWarning {
    val recordingClosed = CountDownLatch(1)
    RecordingStream().use { recording ->
      recording.onClose { recordingClosed.countDown() }
      recording.startAsync()
      ActivityMonitorSampler({ name, _ -> name }, { recording }).use { sampler ->
        assertFalse(sampler.start())
        assertTrue(recordingClosed.await(10, TimeUnit.SECONDS))
        assertFalse(sampler.start())
      }
    }
  }

  @Test
  fun `closed recording disables sampling`() {
    RecordingStream().use { recording ->
      ActivityMonitorSampler({ name, _ -> name }, { recording }).use { sampler ->
        assertTrue(sampler.start())
        recording.close()
        assertFalse(sampler.start())
      }
    }
  }

  @Test
  fun `asynchronous close does not wait for startup or recording shutdown`() {
    val creating = CountDownLatch(1)
    val allowCreation = CountDownLatch(1)
    val closing = CountDownLatch(1)
    val allowClose = CountDownLatch(1)
    val recordingClosed = CountDownLatch(1)
    RecordingStream().use { recording ->
      recording.onClose {
        closing.countDown()
        check(allowClose.await(30, TimeUnit.SECONDS))
        recordingClosed.countDown()
      }
      ActivityMonitorSampler({ name, _ -> name }, {
        creating.countDown()
        check(allowCreation.await(30, TimeUnit.SECONDS))
        recording
      }).use { sampler ->
        Executors.newFixedThreadPool(2).use { executor ->
          val starting = executor.submit<Boolean> { sampler.start() }
          try {
            assertTrue(creating.await(10, TimeUnit.SECONDS))
            executor.submit { sampler.closeAsync() }.get(10, TimeUnit.SECONDS)
            allowCreation.countDown()
            starting.get(10, TimeUnit.SECONDS)
            assertTrue(closing.await(10, TimeUnit.SECONDS))
            assertFalse(executor.submit<Boolean> { sampler.start() }.get(10, TimeUnit.SECONDS))
          }
          finally {
            allowCreation.countDown()
            allowClose.countDown()
          }
        }
        assertTrue(recordingClosed.await(10, TimeUnit.SECONDS))
      }
    }
  }

  @Test
  fun `classification failures log once and sampling continues`() {
    val failure = IllegalStateException("Cannot classify the sample")
    val warnings = AtomicInteger()
    val attempts = AtomicInteger()
    val sampled = CountDownLatch(1)
    val threadName = "activity-failure-test"
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (t === failure) {
          warnings.incrementAndGet()
          return false
        }
        return super.processWarn(category, message, t)
      }
    }).use {
      ActivityMonitorSampler { name, _ ->
        if (name == threadName) {
          if (attempts.incrementAndGet() <= 2) throw failure
          sampled.countDown()
        }
        name
      }.use { sampler ->
        assertTrue(sampler.start())
        val stop = AtomicBoolean()
        val thread = Thread.ofVirtual().name(threadName).start {
          while (!stop.get()) Thread.onSpinWait()
        }
        try {
          assertTrue(sampled.await(20, TimeUnit.SECONDS))
          sampler.close()
          assertTrue(sampler.takeSamples().containsKey(threadName))
          assertEquals(1, warnings.get())
        }
        finally {
          stop.set(true)
          thread.join(10_000)
        }
      }
    }
  }

  @Test
  fun `samples identify platform and virtual workers`() {
    val names = setOf("activity-platform-test", "activity-virtual-test")
    val observed = ConcurrentHashMap.newKeySet<String>()
    val sampled = CountDownLatch(2)
    ActivityMonitorSampler { name, classes ->
      if (name in names && classes.contains(ActivityMonitorSamplerTest::class.java.name) && observed.add(name)) {
        sampled.countDown()
      }
      name
    }.use { sampler ->
      sampler.start()
      val stop = AtomicBoolean()
      val work = Runnable {
        while (!stop.get()) Thread.onSpinWait()
      }
      val platform = Thread.ofPlatform().name("activity-platform-test").start(work)
      val virtual = Thread.ofVirtual().name("activity-virtual-test").start(work)
      try {
        assertTrue(sampled.await(20, TimeUnit.SECONDS), "Observed threads: $observed")
        sampler.close()
        assertTrue(sampler.takeSamples().keys.containsAll(names))
      }
      finally {
        stop.set(true)
        platform.join(10_000)
        virtual.join(10_000)
      }
    }
  }

  private fun withStartupWarning(action: () -> Unit) {
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (message == "Cannot start Activity Monitor sampling" && t is IllegalStateException) return false
        return super.processWarn(category, message, t)
      }
    }).use {
      action()
    }
  }
}
