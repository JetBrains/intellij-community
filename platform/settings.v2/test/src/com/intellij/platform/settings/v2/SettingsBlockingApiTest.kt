// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks the blocking write bridge for a caller that cannot suspend (ADR 0032). The test calls it from the test thread,
 * outside a coroutine, as Java code or a driver call does.
 */
@TestApplication
class SettingsBlockingApiTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a blocking write is applied when the call returns`() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
    val context = SettingsContext.app()
    val key = SettingsKey("test.blocking.text", default = "")

    SettingsBlockingApi.set(key, context, "written")

    assertEquals("written", key[context], "the read after the blocking write sees the written value")
  }
}
