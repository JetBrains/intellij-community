package com.intellij.remoteDev.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class UrlUtilTest {
  @Test
  fun addPathSuffix() {
    testAddPathSuffix(
      "http://127.0.0.1/a.file?x=y#z",
      ".sha256",
      "http://127.0.0.1/a.file.sha256?x=y#z"
    )

    testAddPathSuffix(
      "http://127.0.0.1/",
      ".sha256",
      "http://127.0.0.1/.sha256"
    )
  }

  @Test
  fun addPathSuffix_no_path() {
    assertThrows<IllegalStateException> {
      URI("http://127.0.0.1").addPathSuffix("some")
    }
  }

  private fun testAddPathSuffix(original: String, suffix: String, result: String) {
    assertEquals(result, URI(original).addPathSuffix(suffix).toString())
  }
}