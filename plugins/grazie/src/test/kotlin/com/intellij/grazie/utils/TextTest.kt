package com.intellij.grazie.utils

import com.intellij.grazie.utils.Text.looksLikeCode
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TextTest {
  @Test
  fun testLooksLikeCode_method() {
    assertTrue("""
      @Bean
      public PasswordService passwordService() {
        return new PasswordService();
      }
    """.trimIndent().looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_call() {
    assertTrue("foo(bar, goo, doo)".looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_chained_reference() {
    assertTrue("foo.bar.goo".looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_text() {
    assertFalse("Berlin, Munich, Hamburg, Koeln, Duesseldorf, Stuttgart and Frankfurt are cities".looksLikeCode())
    assertFalse("einal, um ganz sicher zu gehen.".looksLikeCode())
    assertFalse("Doxygen: We desperately and definitely need \\a bit of fresh air.".looksLikeCode())
    assertFalse("the channel \"IDEA EAP\". It's an great mistake.".looksLikeCode())
  }
}