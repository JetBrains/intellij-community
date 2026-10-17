// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.StackTraceSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The creation trace of a light file keeps the top frames of the code that creates the file, and it keeps no class loader of
 * that code. A light file of another plugin can outlive the plugin that created it.
 */
internal class LightVirtualFileCreationTraceTest {
  @Test
  fun `a creation trace keeps no class loader of the code that creates the file`() {
    val call = callUnderOwnClassLoader { LightVirtualFileBase.captureCreationTrace() }

    call.loader.ensureCollected()
    assertThat(call.result).isInstanceOf(StackTraceSnapshot::class.java)
  }

  @Test
  fun `a creation trace starts at the code that creates the file and keeps 16 frames`() {
    val frames = (createTrace() as StackTraceSnapshot).toThrowable().stackTrace

    assertThat(frames.first().methodName).isEqualTo("createTrace")
    assertThat(frames).hasSize(16)
  }

  private fun createTrace(): Any = LightVirtualFileBase.captureCreationTrace()
}
