// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InFlightScansTest {
  @Test
  fun `empty registry has no actualizing files`() {
    val scans = InFlightScans()

    assertThat(scans.isNotEmpty()).describedAs("nothing is registered yet").isFalse()
    assertThat(scans.isActualizing(1)).describedAs("no batch contains the file").isFalse()
  }

  @Test
  fun `file added to a batch is actualizing, other files are not`() {
    val scans = InFlightScans()

    scans.begin().add(1)

    assertThat(scans.isNotEmpty()).isTrue()
    assertThat(scans.isActualizing(1)).describedAs("the batch contains the file").isTrue()
    assertThat(scans.isActualizing(2)).describedAs("the batch does not contain this file").isFalse()
  }

  @Test
  fun `file stays actualizing until every batch with it is released`() {
    val scans = InFlightScans()
    val first = scans.begin().also { it.add(1) }
    val second = scans.begin().also { it.add(1) }

    scans.release(first)
    assertThat(scans.isActualizing(1))
      .describedAs("the second session has scanned the file too and has not applied its events")
      .isTrue()

    scans.release(second)
    assertThat(scans.isActualizing(1)).describedAs("no session has events in flight for the file").isFalse()
    assertThat(scans.isNotEmpty()).isFalse()
  }

  @Test
  fun `release is safe to call twice`() {
    val scans = InFlightScans()
    val batch = scans.begin().also { it.add(1) }
    val other = scans.begin().also { it.add(2) }

    scans.release(batch)
    scans.release(batch)

    assertThat(scans.isActualizing(1)).isFalse()
    assertThat(scans.isActualizing(2)).describedAs("the second release must not touch other batches").isTrue()
    scans.release(other)
  }

  @Test
  fun `file of another session is actualizing elsewhere, own file is not`() {
    val scans = InFlightScans()
    val own = scans.begin().also { it.add(1) }
    scans.begin().also { it.add(2) }

    assertThat(scans.isActualizingElsewhere(2, own)).describedAs("another session holds the file").isTrue()
    assertThat(scans.isActualizingElsewhere(1, own)).describedAs("the session has read its own file from the disk").isFalse()
    assertThat(scans.isActualizingElsewhere(3, own)).describedAs("no session holds the file").isFalse()
  }

  @Test
  fun `file that both sessions scanned is not actualizing elsewhere for either of them`() {
    val scans = InFlightScans()
    val first = scans.begin().also { it.add(1) }
    val second = scans.begin().also { it.add(1) }

    assertThat(scans.isActualizingElsewhere(1, first))
      .describedAs("the first session has scanned the file itself, so it does not scan it again")
      .isFalse()
    assertThat(scans.isActualizingElsewhere(1, second)).isFalse()
    assertThat(scans.isActualizing(1)).describedAs("the file is still in flight for any other reader").isTrue()
  }

  @Test
  fun `batch releases itself without the registry`() {
    val scans = InFlightScans()
    val batch = scans.begin().also { it.add(1) }

    batch.release()

    assertThat(scans.isActualizing(1)).describedAs("a session releases its own batch at the end of its life").isFalse()
    assertThat(scans.isNotEmpty()).isFalse()
  }

  @Test
  fun `release all forgets every batch`() {
    val scans = InFlightScans()
    scans.begin().add(1)
    scans.begin().add(2)

    scans.releaseAll()

    assertThat(scans.isNotEmpty()).describedAs("the refresh queue is disposed: nothing stays in flight").isFalse()
    assertThat(scans.isActualizing(1)).isFalse()
    assertThat(scans.isActualizing(2)).isFalse()
  }

  @Test
  fun `concurrent additions to one batch are all visible`() {
    val scans = InFlightScans()
    val batch = scans.begin()
    val threads = 4
    val filesPerThread = 1000
    val executor = Executors.newFixedThreadPool(threads)
    try {
      val start = CountDownLatch(1)
      val futures = (0 until threads).map { thread ->
        executor.submit {
          start.await()
          for (i in 0 until filesPerThread) {
            batch.add(thread * filesPerThread + i)
          }
        }
      }
      start.countDown()
      futures.forEach { it.get(10, TimeUnit.SECONDS) }
    }
    finally {
      executor.shutdownNow()
    }

    val missing = (0 until threads * filesPerThread).filterNot { scans.isActualizing(it) }
    assertThat(missing).describedAs("scan workers add files concurrently, no addition may be lost").isEmpty()
  }
}