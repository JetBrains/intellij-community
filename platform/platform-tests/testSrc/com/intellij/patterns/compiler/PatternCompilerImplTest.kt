// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns.compiler

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PatternCompilerImplTest {
  @Test
  fun concurrentCallersShareOneCompilation() {
    @Suppress("UNCHECKED_CAST")
    val pattern = PatternCompilerImpl<Any>(listOf(SlowPatterns::class.java)).compileElementPattern("slowPattern()")
      as PatternCompilerImpl.LazyPresentablePattern<Any>

    val firstResult = AtomicReference<ElementPattern<Any>>()
    val first = thread { firstResult.set(pattern.compiledPattern) }
    SlowPatterns.compilationStarted.await(10, TimeUnit.SECONDS)
    val secondResult = AtomicReference<ElementPattern<Any>>()
    val second = thread { secondResult.set(pattern.compiledPattern) }
    // The second caller either blocks on the monitor of the pattern or starts a compilation of its own.
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (second.state != Thread.State.BLOCKED && SlowPatterns.compilations.get() == 1 && System.nanoTime() < deadline) {
      Thread.onSpinWait()
    }
    SlowPatterns.compilationMayFinish.countDown()
    first.join()
    second.join()

    assertEquals(1, SlowPatterns.compilations.get())
    assertSame(firstResult.get(), secondResult.get())
  }

  object SlowPatterns {
    val compilations: AtomicInteger = AtomicInteger()
    val compilationStarted: CountDownLatch = CountDownLatch(1)
    val compilationMayFinish: CountDownLatch = CountDownLatch(1)

    @JvmStatic
    fun slowPattern(): ElementPattern<Any> {
      compilations.incrementAndGet()
      compilationStarted.countDown()
      compilationMayFinish.await(10, TimeUnit.SECONDS)
      return StandardPatterns.`object`()
    }
  }
}
