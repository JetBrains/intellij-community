// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.intellij.util.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The Java 8 default of [Java11Shim] reads the stack from a Throwable. The test environment installs the shim of the IDE instead. */
internal class DefaultJava11ShimTest {
  private val shim = DefaultJava11Shim()

  @Test
  fun `the stack trace starts at the caller of the callee and keeps the limit`() {
    val frames = Callee.frames(shim, limit = 2)

    assertThat(frames).hasSize(2)
    assertThat(frames.first().methodName).isEqualTo("the stack trace starts at the caller of the callee and keeps the limit")
  }

  @Test
  fun `the stack trace without a callee starts at the caller of the shim`() {
    val frames = shim.getStackTrace(1)

    assertThat(frames.single().methodName).isEqualTo("the stack trace without a callee starts at the caller of the shim")
  }

  @Test
  fun `the default shim converts the whole stack`() {
    assertThat(shim.walksOnlyRequestedFrames).isFalse()
  }

  private object Callee {
    fun frames(shim: Java11Shim, limit: Int): Array<StackTraceElement> = inner(shim, limit)

    private fun inner(shim: Java11Shim, limit: Int): Array<StackTraceElement> = shim.getStackTrace(limit, Callee::class.java.name)
  }
}
