// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitAssertEqualsMayBeAssertSameInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJUnitAssertEqualsMayBeAssertSameInspectionTest : JUnitAssertEqualsMayBeAssertSameInspectionTestBase() {
  fun `test JUnit 3 highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test extends junit.framework.TestCase { 
        public void testOne() { 
          <warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b); 
        } 
      }
    """.trimIndent())
  }

  fun `test JUnit 3 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Test extends junit.framework.TestCase { 
        public void testOne() { 
          asser<caret>tEquals(A.a, A.b); 
        } 
      }
    """.trimIndent(), """
      class Test extends junit.framework.TestCase { 
        public void testOne() { 
          assertSame(A.a, A.b); 
        } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }

  fun `test JUnit 4 highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test { 
        @org.junit.Test 
        public void test() { 
          org.junit.Assert.<warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b); 
        } 
      }
    """.trimIndent())
  }

  fun `test JUnit 4 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Test { 
        @org.junit.Test 
        public void test() { 
          org.junit.Assert.assert<caret>Equals(A.a, A.b); 
        } 
      }
    """.trimIndent(), """
      import org.junit.Assert;
      
      class Test { 
        @org.junit.Test 
        public void test() { 
          Assert.assertSame(A.a, A.b); 
        } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }
}