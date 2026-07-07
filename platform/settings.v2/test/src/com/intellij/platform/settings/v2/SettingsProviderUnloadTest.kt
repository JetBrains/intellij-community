// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A layer provider that unloads takes its layers along, and the keys they held change for every listener. The test
 * masks the providers itself, because a masked extension point cannot take another extension: disposing the mask is
 * what unloads the provider.
 */
@TestApplication
class SettingsProviderUnloadTest {
  companion object {
    private val unloaded = SettingsKey("test.unload.key", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val app = SettingsContext.app()

  @Test
  fun `a provider that unloads fires the listeners of the keys its layers held`(): Unit = runBlocking {
    val module = Disposer.newDisposable(disposable, "module")
    val unloading = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(unloading), module)
    unloading.seed(app, unloaded, "from unloading")
    awaitEquals("from unloading") { unloaded[app] }
    val seen = AtomicReference<String?>(null)
    val pings = AtomicInteger(0)
    unloaded.addListener(disposable, app) { _, ctx -> seen.set(unloaded[ctx]) }
    unloaded.addListener(disposable) { _, _ -> pings.incrementAndGet() }

    Disposer.dispose(module)

    awaitEquals("def") { seen.get() }
    awaitEquals(true) { pings.get() > 0 }
  }
}
