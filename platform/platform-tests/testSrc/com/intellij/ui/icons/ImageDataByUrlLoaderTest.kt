// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/** `createImageDataLoaderByUrl` accepts only a `file:` URL without a host, and a `jar:` URL of an entry in such a `file:` URL. */
class ImageDataByUrlLoaderTest {
  @Test
  fun `network and malformed URLs are rejected`() {
    val urls = listOf(
      "https://example.com/icon.svg",
      "jar:https://example.com/icons.jar!/icon.svg",
      // a network share on Windows
      "file://server/share/icon.svg",
      "file://localhost/icons/icon.svg",
      "jar:file://server/share/icons.jar!/icon.svg",
      // a network share in the form of `File.toURI` on Windows
      "file:////server/share/icon.svg",
      // a network share with escaped backslashes, `/\\server\share` after decoding
      "file:/%5C%5Cserver%5Cshare%5Cicon.svg",
      "jar:file:/%5C%5Cserver%5Cshare%5Cicons.jar!/icon.svg",
      // a jar: URL that names no entry, and an opaque file: URL, which has no path
      "jar:file:/icons/icons.jar",
      "file:icon.svg",
      "not a URI",
    )
    for (url in urls) {
      assertThrows<IllegalArgumentException>("$url must be rejected") { createImageDataLoaderByUrl(url) }
    }
  }

  @Test
  fun `local URLs are accepted`() {
    for (url in listOf("file:/icons/icon.svg", "file:///icons/icon.svg", "jar:file:/icons/icons.jar!/icon.svg",
                       "jar:file:///icons/icons.jar!/icon.svg")) {
      assertDoesNotThrow("$url must be accepted") { createImageDataLoaderByUrl(url) }
    }
  }
}
