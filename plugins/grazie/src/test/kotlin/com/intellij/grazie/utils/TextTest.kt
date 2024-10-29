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
  fun testLooksLikeCode_python_f_string() {
    assertTrue("# def_context = f\"Definition of {selection".looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_yaml() {
    assertTrue("sources:'.\nlimits:\n    cpu: 100".looksLikeCode())
    assertTrue("sources:'.\nlimits:\ncpu: 100".looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_escape() {
    assertTrue("foo\\nbar".looksLikeCode())
  }

  @Test
  fun testLooksLikeCode_text() {
    assertFalse("Berlin, Munich, Hamburg, Koeln, Duesseldorf, Stuttgart and Frankfurt are cities".looksLikeCode())
    assertFalse("einal, um ganz sicher zu gehen.".looksLikeCode())
    assertFalse("Doxygen: We desperately and definitely need \\a bit of fresh air.".looksLikeCode())
    assertFalse("the channel \"IDEA EAP\". It's an great mistake.".looksLikeCode())
    assertFalse(".\n Vitorino Nemésio (1901 - 1978) — writer and university".looksLikeCode())
    assertFalse("Vitorino Nemesio (1901 - 1978) - writer and university".looksLikeCode())
  }

  @Test
  fun `testLooksLikeCode c# generic`() {
    assertTrue("public void Method<T>(T string)".looksLikeCode())
    assertTrue("public void Method<TA, TB, TC>()".looksLikeCode())
    assertTrue("public void Method<TA, in TB,out    TC>()".looksLikeCode())
    assertTrue("public void Method<in (TA, TB, TC)>()".looksLikeCode())
  }

  @Test
  fun `testLooksLikeCode Java loop`() {
    assertTrue("for (Person person : people)".looksLikeCode())
  }

  @Test
  fun `testLooksLikeCode Scala`() {
    assertTrue("given j: Int = ???".looksLikeCode())
  }
}