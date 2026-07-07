// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks the key filter of a layer provider, [LayerProvider.acceptsKey], on a read and on a write (ADR 0129). For a
 * key that a provider rejects, the layers of that provider do not exist. A read falls through them, and a write skips them.
 */
@TestApplication
class SettingsLayerKeyFilterTest {
  companion object {
    private val rejected = SettingsKey("test.layerKeyFilter.rejected", default = "def")
    private val onlyInRejecting = SettingsKey("test.layerKeyFilter.onlyInRejecting", default = "def")
    private val accepted = SettingsKey("test.layerKeyFilter.accepted", default = "def")

    private val rejectedIdentities = setOf(rejected.identity, onlyInRejecting.identity)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val wider = TestLayerProvider("wider")
  private val rejecting = TestLayerProvider("rejecting") { it.identity !in rejectedIdentities }

  @Test
  fun `a read ignores the value that a layer holds for a key its provider rejects`(): Unit = runBlocking {
    // The last provider ranks highest on read, so the rejecting one would win without its key filter.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(wider, rejecting), disposable)
    val app = SettingsContext.app()

    wider.seed(app, rejected, "wider")
    rejecting.seed(app, rejected, "rejecting")
    rejecting.seed(app, onlyInRejecting, "rejecting")
    wider.seed(app, accepted, "wider")
    rejecting.seed(app, accepted, "rejecting")

    // The vault installs the pushes in order, so the last seed in place means that all of them are.
    awaitEquals("rejecting") { accepted[app] }
    assertEquals("wider", rejected[app])
    assertEquals("def", onlyInRejecting[app])
  }

  @Test
  fun `a write skips a layer whose provider rejects the key and lands in the next one`(): Unit = runBlocking {
    // A write tries the last provider first, so the rejecting one would take the write without its key filter.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(wider, rejecting), disposable)
    val app = SettingsContext.app()

    rejected[app] = "written"
    SettingsVault.getInstance().flush(app)

    assertEquals(StringValue("written"), wider.saved(app, LayerLocality.LOCAL)?.values?.get(rejected.identity))
    assertNull(rejecting.saved(app, LayerLocality.LOCAL))
  }
}
