// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitIgnoredTestInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJUnitIgnoredTestInspectionTest : JUnitIgnoredTestInspectionTestBase() {
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