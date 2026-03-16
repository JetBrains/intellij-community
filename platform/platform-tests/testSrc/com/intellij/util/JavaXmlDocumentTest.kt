// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.StringReader
import java.nio.file.Files

class JavaXmlDocumentTest {
  @Test
  fun `default builder parses regular xml`() {
    val xml = "<root><value>ok</value></root>"
    val document = createDocumentBuilder().parse(InputSource(StringReader(xml)))

    assertThat(document.documentElement.tagName).isEqualTo("root")
  }

  @Test
  fun `default builder rejects doctype`() {
    val xml = "<!DOCTYPE root><root/>"

    val result = runCatching { createDocumentBuilder().parse(InputSource(StringReader(xml))) }
    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `allowDoctype builder accepts doctype declaration`() {
    val xml = "<!DOCTYPE root><root/>"

    val document = createDocumentBuilder(allowDoctype = true).parse(InputSource(StringReader(xml)))
    assertThat(document.documentElement.tagName).isEqualTo("root")
  }

  @Test
  fun `allowDoctype builder does not resolve external entities`() {
    val secret = "super_secret"
    val file = Files.createTempFile("xml-secret", ".txt")
    Files.writeString(file, secret)

    try {
      val uri = file.toUri()
      val xml = """
        <?xml version="1.0"?>
        <!DOCTYPE root [
          <!ENTITY leak SYSTEM "$uri">
        ]>
        <root>&leak;</root>
      """.trimIndent()

      val result = runCatching {
        createDocumentBuilder(allowDoctype = true).parse(InputSource(StringReader(xml))).documentElement.textContent
      }

      assertThat(result.getOrNull()).isNotEqualTo(secret)
    }
    finally {
      Files.deleteIfExists(file)
    }
  }
}
