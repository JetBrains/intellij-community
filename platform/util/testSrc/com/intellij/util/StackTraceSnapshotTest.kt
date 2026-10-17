// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.intellij.util

import com.intellij.testFramework.callUnderOwnClassLoader
import com.intellij.util.containers.DefaultJava11Shim
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** A [StackTraceSnapshot] keeps the frames of the stack of its caller as data, and it keeps no class loader of the code on that stack. */
internal class StackTraceSnapshotTest {
  @Test
  fun `a snapshot starts at its caller and keeps at most the limit of frames`() {
    val frames = captureHere(limit = 3).toThrowable().stackTrace

    assertThat(frames).hasSize(3)
    assertThat(frames.first().methodName).isEqualTo("captureHere")
  }

  @Test
  fun `a snapshot of the caller of a class skips the frames of that class`() {
    val frames = Callee.capture().toThrowable().stackTrace

    assertThat(frames.first().methodName).isEqualTo("a snapshot of the caller of a class skips the frames of that class")
  }

  @Test
  fun `a rebuilt throwable carries the message and the frames, and the text prints the frames`() {
    val snapshot = captureHere(limit = 2)
    val throwable = snapshot.toThrowable("the place")

    assertThat(throwable.message).isEqualTo("the place")
    assertThat(throwable.stackTrace.first().methodName).isEqualTo("captureHere")
    assertThat(snapshot.toString()).startsWith("\tat ${StackTraceSnapshotTest::class.java.name}.captureHere(")
  }

  @Test
  fun `a snapshot keeps no class loader of the code on its stack`() {
    val call = callUnderOwnClassLoader { StackTraceSnapshot.capture() }

    call.loader.ensureCollected()
    assertThat(call.result.toString()).contains("com.intellij.testFramework.OwnClassLoaderCaller.apply(")
  }

  @Test
  fun `a hot path snapshot without a fast shim keeps the stack and gives the same frames`() {
    val frames = captureOnHotPathHere(limit = 2).toThrowable().stackTrace

    assertThat(frames).hasSize(2)
    assertThat(frames.first().methodName).isEqualTo("captureOnHotPathHere")
  }

  private fun captureHere(limit: Int): StackTraceSnapshot = StackTraceSnapshot.capture(limit)

  private fun captureOnHotPathHere(limit: Int): StackTraceSnapshot {
    return StackTraceSnapshot.captureOnHotPath(DefaultJava11Shim(), limit, emptyArray())
  }

  private object Callee {
    fun capture(): StackTraceSnapshot = inner()

    private fun inner(): StackTraceSnapshot = StackTraceSnapshot.captureCallerOf(Callee::class.java, 16)
  }
}
