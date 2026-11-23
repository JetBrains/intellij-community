// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val descriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT4)

@TestApplication
class JavaExpectedExceptionNeverThrownInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(descriptor)
  }

  private val myFixture by jvmInspectionFixture(projectFixture) { ExpectedExceptionNeverThrownInspection() }

  @Test
  fun testSimple() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected=<warning descr="Expected 'java.io.IOException' never thrown in body of 'test()'">java.io.IOException</warning>.class)
        public void test() { }
      }
    """.trimIndent())
  }

  @Test
  fun testInheritance() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected=java.io.EOFException.class)
        public void test() throws Exception {
          foo();
        }
        
        void foo() throws java.io.IOException { }
      }
    """.trimIndent())
  }

  @Test
  fun testError() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected = Error.class)
        public void test() { }
      }
    """.trimIndent())
  }

  @Test
  fun testRuntimeException() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class X {
        @org.junit.Test(expected = IllegalArgumentException.class)
        public void test() {}
      }
    """.trimIndent())
  }
}
