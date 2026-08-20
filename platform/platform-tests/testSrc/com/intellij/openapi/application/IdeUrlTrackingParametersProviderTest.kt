// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class IdeUrlTrackingParametersProviderTest {
  @Test
  fun `getInstance returns shared identity fallback when no service is registered`(@TestDisposable disposable: Disposable) {
    MockApplication.setUp(disposable)
    assertNull(ApplicationManager.getApplication().getService(IdeUrlTrackingParametersProvider::class.java))
    val provider = IdeUrlTrackingParametersProvider.getInstance()
    assertSame(provider, IdeUrlTrackingParametersProvider.getInstance())
    listOf("https://www.jetbrains.com/idea/", "https://example.com/download?platform=mac").forEach { url ->
      assertEquals(url, provider.augmentUrl(url))
      assertEquals(url, provider.augmentUrl(url, "campaign"))
    }
  }
}
