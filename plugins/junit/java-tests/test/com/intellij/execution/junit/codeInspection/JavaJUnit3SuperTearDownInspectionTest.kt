// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.junit.testFramework.junit3SuperTearDownInspectionFixture
import com.intellij.junit.testFramework.junitProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaJUnit3SuperTearDownInspectionTest {
  companion object {
    private val projectFixture = junitProjectFixture(JUNIT3)
  }

  private val myFixture by junit3SuperTearDownInspectionFixture(projectFixture)

  @Test
  fun `test teardown in finally no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class NoProblem extends junit.framework.TestCase {
        public void tearDown() throws Exception {
          super.tearDown();
        }
      }
      class CalledInFinally extends junit.framework.TestCase {
        public void tearDown() throws Exception {
          try {
            System.out.println();
          } finally {
            super.tearDown();
          }
        }
      }
      class SomeTest extends junit.framework.TestCase {
        @Override
        protected void setUp() throws Exception {
          try {
            super.setUp();
          }
          catch (Throwable t) {
            super.tearDown();
          }
        }
        public void test_something() {}
      }
    """.trimIndent())
  }

  @Test
  fun `test teardown in finally highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class SuperTearDownInFinally extends junit.framework.TestCase {
        @Override
        public void tearDown() throws Exception {
          super.<warning descr="'tearDown()' is not called from 'finally' block">tearDown</warning>();
          System.out.println();
        }
      }      
    """.trimIndent())
  }
}