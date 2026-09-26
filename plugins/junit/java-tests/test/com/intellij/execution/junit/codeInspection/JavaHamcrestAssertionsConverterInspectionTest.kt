// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.HamcrestAssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaHamcrestAssertionsConverterInspectionTest : HamcrestAssertionsConverterInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Assert;
      import java.util.Collection;

      class Foo {
        void m() {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 != 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 == 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 > 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 < 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 >= 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 <= 3);

          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 != 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 == 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 > 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 < 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 >= 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 <= 3);
        }

        void m2() {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".equals("zxc"));
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd" == "zxc");
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".contains("qwe"));
        }

        void m3(Collection<String> c, String o) {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(c.contains(o));
          Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>(c, o);
          Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>("msg", c, o);
          Assert.<warning descr="Assert expression 'assertNotNull' can be replaced with 'assertThat()' call">assertNotNull</warning>(c);
          Assert.<warning descr="Assert expression 'assertNull' can be replaced with 'assertThat()' call">assertNull</warning>(c);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(c.contains(o));
        }

        void m(int[] a, int[] b) {
          Assert.<warning descr="Assert expression 'assertArrayEquals' can be replaced with 'assertThat()' call">assertArrayEquals</warning>(a, b);
        }
      }      
    """.trimIndent())
  }

  fun `test quickfix binary expression`() {
    myFixture.testAllQuickfixes(JvmLanguage.JAVA, """
      import org.junit.Assert;

      class MigrationTest {
        void migrate() {
          Assert.assertTrue(2 != 3);
          Assert.assertTrue(2 == 3);
          Assert.assertTrue(2 > 3);
          Assert.assertTrue(2 < 3);
          Assert.assertTrue(2 >= 3);
          Assert.assertTrue(2 <= 3);
          Assert.assertFalse(2 != 3);
          Assert.assertFalse(2 == 3);
          Assert.assertFalse(2 > 3);
          Assert.assertFalse(2 < 3);
          Assert.assertFalse(2 >= 3);
          Assert.assertFalse(2 <= 3);
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;
      
      import static org.hamcrest.MatcherAssert.*;
      import static org.hamcrest.Matchers.*;

      class MigrationTest {
        void migrate() {
          assertThat(2, not(is(3)));
          assertThat(2, is(3));
          assertThat(2, greaterThan(3));
          assertThat(2, lessThan(3));
          assertThat(2, greaterThanOrEqualTo(3));
          assertThat(2, lessThanOrEqualTo(3));
          assertThat(2, is(3));
          assertThat(2, not(is(3)));
          assertThat(2, lessThanOrEqualTo(3));
          assertThat(2, greaterThanOrEqualTo(3));
          assertThat(2, lessThan(3));
          assertThat(2, greaterThan(3));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix string`() {
    myFixture.testAllQuickfixes(JvmLanguage.JAVA, """
      import org.junit.Assert;

      class Foo {
        void migrate() {
          Assert.assertTrue("asd".equals("zxc"));
          Assert.assertTrue("asd" == "zxc");
          Assert.assertTrue("asd".contains("qwe"));
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;
      
      import static org.hamcrest.MatcherAssert.*;
      import static org.hamcrest.Matchers.*;

      class Foo {
        void migrate() {
          assertThat("asd", is("zxc"));
          assertThat("asd", sameInstance("zxc"));
          assertThat("asd", containsString("qwe"));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix collection`() {
    myFixture.testAllQuickfixes(JvmLanguage.JAVA, """
      import org.junit.Assert;
      import java.util.Collection;
      
      import static org.hamcrest.MatcherAssert.*;
      import static org.hamcrest.Matchers.*;      

      class Foo {
        void migrate(Collection<String> c, String o) {
          Assert.assertTrue(c.contains(o));
          Assert.assertEquals(c, o);
          Assert.assertEquals("msg", c, o);
          Assert.assertNotNull(c);
          Assert.assertNull(c);
          Assert.assertFalse(c.contains(o));
        }
      }      
    """.trimIndent(), """
      import org.junit.Assert;
      import java.util.Collection;
      
      import static org.hamcrest.MatcherAssert.*;
      import static org.hamcrest.Matchers.*;      

      class Foo {
        void migrate(Collection<String> c, String o) {
          assertThat(c, hasItem(o));
          assertThat(o, is(c));
          assertThat("msg", o, is(c));
          assertThat(c, notNullValue());
          assertThat(c, nullValue());
          assertThat(c, not(hasItem(o)));
        }
      }      
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix array`() {
    myFixture.testAllQuickfixes(JvmLanguage.JAVA, """
      import org.junit.Assert;

      class Foo {
        void migrate(int[] a, int[] b) {
          Assert.assertArrayEquals(a, b);
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;
      
      import static org.hamcrest.MatcherAssert.*;
      import static org.hamcrest.Matchers.*;

      class Foo {
        void migrate(int[] a, int[] b) {
          assertThat(b, is(a));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }
}