// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchEngineTest {
  @Test
  fun `each engine searches over https with the encoded query`() {
    for (engine in SearchEngine.entries) {
      val url = engine.searchUrl("a&b=c d")
      assertTrue(url.startsWith("https://") && url.endsWith("=a%26b%3Dc%20d"), "$engine: $url")
    }
  }
}
