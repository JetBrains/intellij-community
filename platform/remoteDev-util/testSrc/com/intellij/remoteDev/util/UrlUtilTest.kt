package com.intellij.remoteDev.util

import org.junit.Assert
import org.junit.Test
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

  @Test(expected = IllegalStateException::class)
  fun addPathSuffix_no_path() {
    URI("http://127.0.0.1").addPathSuffix("some")
  }

  private fun testAddPathSuffix(original: String, suffix: String, result: String) {
    Assert.assertEquals(result, URI(original).addPathSuffix(suffix).toString())
  }
}