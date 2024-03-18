// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.HamcrestAssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

class KotlinHamcrestAssertionsConverterInspectionTest : HamcrestAssertionsConverterInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun m() {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 != 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 == 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 > 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 < 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 >= 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 <= 3)
    
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 != 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 == 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 > 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 < 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 >= 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 <= 3)
          }
  
          fun m2() {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".equals("zxc"))
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd" == "zxc")
          }
  
          fun m3(c: Collection<String>, o: String) {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(c.contains(o))
              Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>(c, o)
              Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>("msg", c, o)
              Assert.<warning descr="Assert expression 'assertNotNull' can be replaced with 'assertThat()' call">assertNotNull</warning>(c)
              Assert.<warning descr="Assert expression 'assertNull' can be replaced with 'assertThat()' call">assertNull</warning>(c)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(c.contains(o))
          }
  
          fun m(a: IntArray, b: IntArray) {
              Assert.<warning descr="Assert expression 'assertArrayEquals' can be replaced with 'assertThat()' call">assertArrayEquals</warning>(a, b)
          }
      }      
    """.trimIndent())
  }

  fun `test quickfix binary expression`() {
    myFixture.testAllQuickfixes(JvmLanguage.KOTLIN, """
      import org.junit.Assert

      class MigrationTest {
          fun migrate() {
              Assert.assertTrue(2 != 3)
              Assert.assertTrue(2 == 3)
              Assert.assertTrue(2 > 3)
              Assert.assertTrue(2 < 3)
              Assert.assertTrue(2 >= 3)
              Assert.assertTrue(2 <= 3)
              Assert.assertFalse(2 != 3)
              Assert.assertFalse(2 == 3)
              Assert.assertFalse(2 > 3)
              Assert.assertFalse(2 < 3)
              Assert.assertFalse(2 >= 3)
              Assert.assertFalse(2 <= 3)
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.MatcherAssert.*
      import org.hamcrest.Matchers
      import org.hamcrest.Matchers.*
      import org.junit.Assert
      
      class MigrationTest {
          fun migrate() {
              assertThat(2, not(`is`(3)))
              assertThat(2, `is`(3))
              assertThat(2, greaterThan(3))
              assertThat(2, lessThan(3))
              assertThat(2, greaterThanOrEqualTo(3))
              assertThat(2, lessThanOrEqualTo(3))
              assertThat(2, `is`(3))
              assertThat(2, not(`is`(3)))
              assertThat(2, lessThanOrEqualTo(3))
              assertThat(2, greaterThanOrEqualTo(3))
              assertThat(2, lessThan(3))
              assertThat(2, greaterThan(3))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix string`() {
    myFixture.testAllQuickfixes(JvmLanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate() {
              Assert.assertTrue("asd".equals("zxc"))
              Assert.assertTrue("asd" === "zxc")
              Assert.assertTrue("asd".contains("zxc"))
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.MatcherAssert.*
      import org.hamcrest.Matchers
      import org.hamcrest.Matchers.*
      import org.junit.Assert
      
      class Foo {
          fun migrate() {
              assertThat("asd", `is`("zxc"))
              assertThat("asd", sameInstance("zxc"))
              assertThat("asd", containsString("zxc"))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix collection`() {
    myFixture.testAllQuickfixes(JvmLanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate(c: Collection<String>, o: String) {
              Assert.assertTrue(c.contains(o))
              Assert.assertEquals(c, o)
              Assert.assertEquals("msg", c, o)
              Assert.assertNotNull(c)
              Assert.assertNull(c)
              Assert.assertFalse(c.contains(o))
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.MatcherAssert.*
      import org.hamcrest.Matchers
      import org.hamcrest.Matchers.*
      import org.junit.Assert
      
      class Foo {
          fun migrate(c: Collection<String>, o: String) {
              assertThat(c, hasItem(o))
              assertThat(o, `is`(c))
              assertThat("msg", o, `is`(c))
              assertThat(c, notNullValue())
              assertThat(c, nullValue())
              assertThat(c, not(hasItem(o)))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix array`() {
    myFixture.testAllQuickfixes(JvmLanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate(a: IntArray, b: IntArray) {
              Assert.assertArrayEquals(a, b)
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.MatcherAssert.*
      import org.hamcrest.Matchers
      import org.hamcrest.Matchers.*
      import org.junit.Assert
      
      class Foo {
          fun migrate(a: IntArray, b: IntArray) {
              assertThat(b, `is`(a))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }
}