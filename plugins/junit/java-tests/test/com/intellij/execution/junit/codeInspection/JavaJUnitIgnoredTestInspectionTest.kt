// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.junit.testFramework.junitIgnoredTestInspectionFixture
import com.intellij.junit.testFramework.junitProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaJUnitIgnoredTestInspectionTest {
  companion object {
    private val projectFixture = junitProjectFixture(JUNIT4, JUNIT5)
  }

  private val myFixture by junitIgnoredTestInspectionFixture(projectFixture)

  @Test
  fun `test JUnit 4 @Ignore`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.*;

      @Ignore("for good reason")
      class IgnoredJUnitTest {
        @Ignore
        @Test
        public void <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Ignore("valid description")
        @Test
        public void foo2() { }        
      }
    """.trimIndent())
  }

  @Test
  fun `test JUnit 5 @Disabled`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      import org.junit.Ignore;

      @Disabled
      class <warning descr="Test class 'DisabledJUnit5Test' is ignored/disabled without reason">DisabledJUnit5Test</warning> {
        @Disabled
        @Test
        void <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Disabled
        @Ignore("valid reason")
        @Test
        void foo2() { }        
      }
    """.trimIndent())
  }
}