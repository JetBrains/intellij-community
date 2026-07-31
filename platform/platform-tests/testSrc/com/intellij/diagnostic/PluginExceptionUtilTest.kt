// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@TestApplication
class PluginExceptionUtilTest {
  @Test
  fun `computeWithPluginExceptions returns success`() {
    val result = PluginExceptionUtil.computeWithPluginExceptions(javaClass) { "ok" }
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()).isEqualTo("ok")
  }

  @Test
  fun `computeWithPluginExceptions wraps arbitrary exception into an attributed PluginException`() {
    val cause = RuntimeException("boom")
    val result = PluginExceptionUtil.computeWithPluginExceptions(javaClass) { throw cause }
    assertThat(result.isFailure).isTrue()
    val error = result.exceptionOrNull()
    assertThat(error).isInstanceOf(PluginException::class.java)
    assertThat(error!!.cause).isSameAs(cause)
  }

  @Test
  fun `computeWithPluginExceptions uses the given message for the wrapping PluginException`() {
    val cause = RuntimeException("boom")
    val result = PluginExceptionUtil.computeWithPluginExceptions(javaClass, "custom message") { throw cause }
    assertThat(result.isFailure).isTrue()
    val error = result.exceptionOrNull()
    assertThat(error).isInstanceOf(PluginException::class.java)
    assertThat(error!!.message).contains("custom message")
    assertThat(error.cause).isSameAs(cause)
  }

  @Test
  fun `computeWithPluginExceptions rethrows IndexNotReadyException unchanged`() {
    val indexNotReady = IndexNotReadyException.create()
    assertThatThrownBy {
      PluginExceptionUtil.computeWithPluginExceptions(javaClass) { throw indexNotReady }
    }.isSameAs(indexNotReady)
  }

  @Test
  fun `computeWithPluginExceptions rethrows control-flow exception`() {
    assertThatThrownBy {
      PluginExceptionUtil.computeWithPluginExceptions(javaClass) { throw ProcessCanceledException() }
    }.isInstanceOf(ProcessCanceledException::class.java)
  }

  @Test
  fun `computeOrLogPluginException returns value on success`() {
    val value = PluginExceptionUtil.computeOrLogPluginException(javaClass) { "ok" }
    assertThat(value).isEqualTo("ok")
  }

  @Test
  fun `computeOrLogPluginException logs an attributed PluginException and returns null on failure`() {
    val cause = RuntimeException("boom")
    var value: Any? = "not-set"
    val logged = LoggedErrorProcessor.executeAndReturnLoggedError {
      value = PluginExceptionUtil.computeOrLogPluginException(javaClass) { throw cause }
    }
    assertThat(value).isNull()
    assertThat(logged).isInstanceOf(PluginException::class.java)
    assertThat(logged.cause).isSameAs(cause)
  }

  @Test
  fun `computeOrLogPluginException rethrows control-flow exception without logging`() {
    assertThatThrownBy {
      PluginExceptionUtil.computeOrLogPluginException(javaClass) { throw ProcessCanceledException() }
    }.isInstanceOf(ProcessCanceledException::class.java)
  }
}
