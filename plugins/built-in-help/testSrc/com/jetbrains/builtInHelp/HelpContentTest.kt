// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelpContentTest {
  @Test
  fun `localized pages fall back to English`() {
    assertTrue(Utils.getResourceWithFallback("topics", "index.html", "zh-cn")!!.decodeToString().contains("离线帮助"))
    assertTrue(Utils.getResourceWithFallback("topics", "editor.html", "zh-cn")!!.decodeToString().contains("Editor settings"))
    assertNotNull(Utils.getResourceWithFallback("images", "example.svg", "zh-cn"))
    assertNull(Utils.getResourceWithFallback("topics", "missing.html", "zh-cn"))
  }

  @Test
  fun `topic maps preserve fragments and supply a default`() {
    val map = HelpContentRequestHandler().loadMap("zh-cn")!!
    assertEquals("editor.html#shortcuts", map.getUrlForId("editor.settings"))
    assertEquals("index.html", map.getUrlForId("unknown"))
  }

  @Test
  fun `routes do not match query strings or unrelated paths`() {
    val handler = HelpContentRequestHandler()
    assertTrue(handler.matchesPath("/help/index.html"))
    assertTrue(handler.matchesPath("/zh-cn/help/index.html"))
    assertFalse(handler.matchesPath("/other/help/index.html"))
    assertFalse(handler.matchesPath("/search/"))
  }

  @Test
  fun `offline configuration retains unrelated strings`() {
    val content = Utils.getResourceWithFallback("topics", "config.json", "")!!
    val result = HelpContentRequestHandler().rewriteConfig(content, "http://127.0.0.1:63342/zh-cn").decodeToString()
    assertTrue(result.contains("true story"))
    assertTrue(result.contains("http://127.0.0.1:63342/zh-cn/search/"))
    assertFalse(result.contains("searchAlgoliaApiKey"))
    assertFalse(result.contains("versionsService"))
  }
}
