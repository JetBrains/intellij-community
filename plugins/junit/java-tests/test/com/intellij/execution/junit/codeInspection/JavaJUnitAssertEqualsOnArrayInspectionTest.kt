// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.junit.testFramework.junitAssertEqualsOnArrayInspectionFixture
import com.intellij.junit.testFramework.junitProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaJUnitAssertEqualsOnArrayInspectionTest {
  companion object {
    private val projectFixture = junitProjectFixture(JUNIT5)
  }

  private val myFixture by junitAssertEqualsOnArrayInspectionFixture(projectFixture)

  @Test
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.<warning descr="'assertEquals()' called on array">assertEquals</warning>(a, e, "message");
        }
      }      
    """.trimIndent())
  }

  @Test
  fun `test quickfix`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.assert<caret>Equals(a, e, "message");
        }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.assertArrayEquals(a, e, "message");
        }
      }
    """.trimIndent(), "Replace with 'assertArrayEquals()'")
  }
}