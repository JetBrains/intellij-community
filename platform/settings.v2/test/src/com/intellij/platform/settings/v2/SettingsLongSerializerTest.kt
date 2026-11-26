// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks that a [Long] key reads a small number of a config file. The JSON codec reads a number as the smallest of
 * int, long and double that holds it, so a small long comes back as an int value. A test layer holds the values, and
 * the production codec converts them to and from the text of a config file.
 */
@TestApplication
class SettingsLongSerializerTest {
  companion object {
    private val marker = SettingsKey("test.long.marker", default = "")
    private val written = SettingsKey("test.long.written", default = -1L)
    private val handWritten = SettingsKey("test.long.handWritten", default = -1L)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val app = SettingsContext.app()

  @Test
  fun `a long key reads a small long that it wrote to a config file`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    written[app] = 5L
    SettingsVault.getInstance().flush(app)
    val configText = JsonSettingsCodec.encode(requireNotNull(provider.saved(app, LayerLocality.LOCAL)))
    val readConfig = JsonSettingsCodec.decode(configText)
    assertEquals(IntValue(5), readConfig.values[written.identity], "the codec reads the small long as an int value")

    provider.seed(app, MapValue(readConfig.values + (marker.identity to StringValue("landed"))), LayerLocality.LOCAL)
    awaitEquals("landed") { marker[app] }

    assertEquals(5L, written[app])
  }

  @Test
  fun `a long key reads a small hand-written number of a config file`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    provider.seed(app, JsonSettingsCodec.decode("""{"${marker.identity}": "landed", "${handWritten.identity}": 5}"""))
    awaitEquals("landed") { marker[app] }

    assertEquals(5L, handWritten[app])
  }
}
