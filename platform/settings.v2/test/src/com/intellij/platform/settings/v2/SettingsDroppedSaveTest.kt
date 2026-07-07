// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks a save that the provider drops. The store has no place, so the vault removes every layer of the store. This
 * includes a layer that a write changed while the save ran (ADR 0150).
 */
@TestApplication
class SettingsDroppedSaveTest {
  companion object {
    private val key = SettingsKey("test.dropped.key", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()

  @Test
  fun `a write during a save that comes back dropped is dropped too`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
    val heldSave = provider.holdNextSave()

    key[app] = "first"
    withTimeout(10.seconds) { heldSave.started.await() }
    key[app] = "second"
    heldSave.release.complete(Unit)

    awaitEquals("def") { key[app] }
    // The second write scheduled a save of its own. The drop cleared it, so the provider sees no other call.
    delay(1.seconds)
    assertEquals(1, provider.saveAttempts.get())
  }
}
