// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The echo guard: while a save of a store is pending, the vault ignores what the provider reports for that store.
 * Such a report holds data from before the write, for example the watcher echo of an earlier save.
 */
@TestApplication
class SettingsPendingSaveTest {
  companion object {
    private val key = SettingsKey("test.pendingSave.key", default = "def")
    private val fence = SettingsKey("test.pendingSave.fence", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()

  @Test
  fun `a push for a store with a pending save is ignored`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
    val heldSave = provider.holdNextSave(SaveResult.SAVED)

    key[app] = "written"
    withTimeout(10.seconds) { heldSave.started.await() }
    provider.seed(app, key, "stale", LayerLocality.LOCAL)
    // A push to another store applies at once, and the vault takes the pushes in order, so the stale one was seen.
    provider.seed(app, fence, "fenced", LayerLocality.TEAM)
    awaitEquals("fenced") { fence[app] }
    heldSave.release.complete(Unit)

    assertEquals("written", key[app])
  }
}
