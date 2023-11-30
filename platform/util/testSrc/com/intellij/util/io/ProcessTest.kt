// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.util.io

import com.intellij.testFramework.pollAssertionsAsync
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@CoroutinesTimeout(3_000)
class ProcessTest {
  @Nested
  inner class copyToAsync {
    @Test
    fun `simple copying`(): Unit = runBlocking {
      val outputStream = ByteArrayOutputStream()
      val expectedBytes = "hello world".toByteArray()
      ByteArrayInputStream(expectedBytes).copyToAsync(outputStream)
      assertThat(outputStream.toByteArray()).isEqualTo(expectedBytes)
    }

    @ParameterizedTest
    @ValueSource(ints = [1 shl 8, 1 shl 10, 7919, 1 shl 20, 1 shl 21])
    fun `copy 1 MiB`(bufferSize: Int): Unit = runBlocking {
      val outputStream = ByteArrayOutputStream()
      val expectedBytes = ByteArray(1 shl 20) { it.toByte() }
      ByteArrayInputStream(expectedBytes).copyToAsync(outputStream, bufferSize = bufferSize)
      assertThat(outputStream.toByteArray()).isEqualTo(expectedBytes)
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, 1L, 7919L, 1L shl 20, 1L shl 21])
    fun `copy 1 MiB with limit`(limit: Long): Unit = runBlocking {
      val outputStream = ByteArrayOutputStream()
      val expectedBytes = ByteArray(1 shl 20) { it.toByte() }
      ByteArrayInputStream(expectedBytes).copyToAsync(outputStream, limit = limit)
      assertThat(outputStream.toByteArray())
        .isEqualTo(ByteArray(expectedBytes.size.coerceAtMost(limit.toInt())) { expectedBytes[it] })
    }

    @Test
    @CoroutinesTimeout(30_000)
    fun `cancel by timeout`(): Unit = runBlocking {
      pollAssertionsAsync(total = 15.seconds, interval = ZERO) {
        val timeout = 500.milliseconds
        val threshold = 100.milliseconds
        val multiplier = 3

        val holdingThread = AtomicBoolean(false)

        val delegate = ByteArrayInputStream("hello world".toByteArray())

        val inputStream = object : InputStream() {
          private val shouldBeSlow = AtomicBoolean()

          override fun read(): Int {
            error("Must not be called")
          }

          override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (shouldBeSlow.getAndSet(true)) {
              try {
                holdingThread.set(true)
                Thread.sleep(timeout.inWholeMilliseconds * multiplier)
              }
              finally {
                holdingThread.set(false)
              }
            }
            return delegate.read(b, off, len)
          }
        }

        val startedAt = System.nanoTime().nanoseconds
        val outputStream = ByteArrayOutputStream()
        try {
          withTimeout(timeout) {
            inputStream.copyToAsync(outputStream, bufferSize = 5)
          }
          fail<Any>("Should not reach this point")
        }
        catch (ignored: TimeoutCancellationException) {
          // Ignored.
        }
        val finishedAt = System.nanoTime().nanoseconds

        assertThat(String(outputStream.toByteArray()))
          .describedAs("According to the test logic, part of the data should have been copied")
          .isEqualTo("hello")

        assertThat(finishedAt - startedAt)
          //.describedAs("The coroutine timeout should be honored, despite longer blocking delays")  // Can't uncomment: a bug in assertj.
          .isLessThan(timeout + threshold)

        assertThat(holdingThread.get())
          .describedAs("The thread of copying is expected to be blocked by the copying operation")
          .isTrue()

        delay(timeout * (multiplier - 1) + threshold)

        assertThat(holdingThread.get())
          .describedAs("After a while, no more blocking operations should be invoked. The background operation should've been cancelled.")
          .isFalse()

        assertThat(String(outputStream.toByteArray()))
          .describedAs("According to the test logic, no more data should have been copied")
          .isEqualTo("hello")
      }
    }
  }
}