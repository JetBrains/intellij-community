// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import io.netty.handler.codec.http.QueryStringDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelpUrlsTest {
  @Test
  fun `null topic uses top before encoding`() {
    assertEquals("http://127.0.0.1:63342/help/?top", HelpUrls.entryUrl(63342, null, null, "en-US"))
  }

  @Test
  fun `topic and keymap are encoded once`() {
    val url = HelpUrls.entryUrl(63343, "special & topic", "Mac OS X & custom", "ZH-CN")
    val decoder = QueryStringDecoder(url)
    assertEquals("/zh-cn/help/", java.net.URI(url).path)
    assertEquals(listOf(""), decoder.parameters()["special & topic"])
    assertEquals(listOf("Mac OS X & custom"), decoder.parameters()["keymap"])
  }

  @Test
  fun `keymap is replaced before the fragment`() {
    assertEquals("http://localhost/help/editor.html?x=1&keymap=Mac+OS+X#shortcuts",
                 HelpUrls.withKeymap("http://localhost/help/editor.html?x=1&keymap=old#shortcuts", "Mac OS X"))
  }

  @Test
  fun `legacy homepage and empty base retain the product URL`() {
    val defaultUrl = "https://www.jetbrains.com/help/idea/263/?editor.settings&keymap=Windows"
    for (base in listOf("", "https://www.jetbrains.com/")) {
      assertEquals(defaultUrl, HelpUrls.onlineUrl("editor.settings", "Windows", base, defaultUrl))
    }
  }

  @Test
  fun `custom help root preserves its product and version path`() {
    assertEquals("https://docs.example.com/idea/263/?editor.settings&keymap=Windows",
                 HelpUrls.onlineUrl("editor.settings", "Windows", "https://docs.example.com/idea/263", null))
  }

  @Test
  fun `invalid custom roots are rejected`() {
    for (url in listOf("file:///tmp/help", "https://user:pass@example.com/", "https://example.com/?q=x", "https://example.com/#x")) {
      assertFalse(HelpUrls.isValidBaseUrl(url))
      assertNull(HelpUrls.onlineUrl("top", null, url, null))
    }
    assertTrue(HelpUrls.isValidBaseUrl("http://localhost:8080/docs/"))
  }
}
