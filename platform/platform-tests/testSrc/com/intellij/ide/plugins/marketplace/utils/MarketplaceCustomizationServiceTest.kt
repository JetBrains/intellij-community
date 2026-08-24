// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@TestApplication
internal class MarketplaceCustomizationServiceTest {
  private val testDisposable = disposableFixture()

  @Test
  fun `extension takes precedence over the default service`() {
    val customization = FakeMarketplaceCustomization("https://first.example.com")
    maskCustomizations(customization)

    assertSame(customization, MarketplaceCustomizationService.getInstance())
  }

  @Test
  fun `falls back to the default service when no extension is registered`() {
    maskCustomizations()

    assertSame(service<MarketplaceCustomizationService>(), MarketplaceCustomizationService.getInstance())
  }

  @Test
  fun `only the first extension is used`() {
    val first = FakeMarketplaceCustomization("https://first.example.com")
    maskCustomizations(first, FakeMarketplaceCustomization("https://second.example.com"))

    assertSame(first, MarketplaceCustomizationService.getInstance())
    assertEquals("https://first.example.com", MarketplaceCustomizationService.getInstance().getPluginManagerUrl())
  }

  private fun maskCustomizations(vararg customizations: MarketplaceCustomizationService) {
    ExtensionTestUtil.maskExtensions(MarketplaceCustomizationService.EP, customizations.asList(), testDisposable.get())
  }
}

private class FakeMarketplaceCustomization(private val url: String) : MarketplaceCustomizationService {
  override fun getPluginManagerUrl(): String = url
  override fun getPluginDownloadUrl(): String = "$url/download"
  override fun getPluginsListUrl(): String = "$url/list"
  override fun getPluginHomepageUrl(pluginId: PluginId): String = "$url/plugin/${pluginId.idString}"
}
