// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.testFramework.callUnderOwnClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream

/** A process handler keeps the stack that created it as data, so it keeps no class loader of the code that started the process. */
internal class BaseOSProcessHandlerCreationTraceTest {
  @Test
  fun `a process handler keeps no class loader of the code that created it`() {
    val call = callUnderOwnClassLoader { BaseOSProcessHandler(FinishedProcess(), "finished process", null) }
    call.result.startNotify()
    assertThat(call.result.waitFor(TIMEOUT_MS)).isTrue()

    call.loader.ensureCollected()
    assertThat(call.result.isProcessTerminated).isTrue()
  }
}

/** A process that has already ended with the exit code 0 and has no output. */
private class FinishedProcess : Process() {
  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

  override fun getInputStream(): InputStream = InputStream.nullInputStream()

  override fun getErrorStream(): InputStream = InputStream.nullInputStream()

  override fun waitFor(): Int = 0

  override fun exitValue(): Int = 0

  override fun destroy() {
  }
}

private const val TIMEOUT_MS = 10_000L
