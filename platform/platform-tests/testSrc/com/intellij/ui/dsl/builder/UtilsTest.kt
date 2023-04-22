// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import org.junit.Test
import kotlin.test.assertEquals

class UtilsTest {

  @Test
  fun testCleanupHtml() {
    val testData = mapOf(
      "Hello" to "Hello",
      " Hello " to " Hello ",
      "<html>Hello</html>" to "Hello",
      "   <html> Hello </html>   " to " Hello ",
      "<html>Hello" to "<html>Hello",
    )

    for ((original, expected) in testData) {
      assertEquals(expected, cleanupHtml(original))
    }
  }
}
