// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/** Calls the real `winhttp.dll`. The settings of the machine are unknown, so the test checks only that each call completes. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(60)
internal class WinHttpTest {
  @Test
  fun `the machine-wide configuration is readable`() {
    assertThat(WinHttp.defaultProxyConfig()).isNotNull
  }

  @Test
  fun `the user configuration is readable`() {
    assertThat(WinHttp.currentUserProxyConfig()).isNotNull
  }

  @Test
  fun `the strategy runs against the real settings`() {
    assertThatCode { WindowsProxySearchStrategy().proxySelector }.doesNotThrowAnyException()
  }
}
