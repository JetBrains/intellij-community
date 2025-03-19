// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnit3SuperTearDownInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnit3SuperTearDownInspectionTest : JUnit3SuperTearDownInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test teardown in finally no highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class NoProblem : junit.framework.TestCase() {
        override fun tearDown() {
          super.tearDown();
        }
      }
      class CalledInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          try {
            System.out.println()
          } finally {
            super.tearDown()
          }
        }
      }
      class SomeTest : junit.framework.TestCase() {
        override fun setUp() {
          try {
            super.setUp()
          }
          catch (t: Throwable) {
            super.tearDown()
          }
        }
        fun test_something() { }
      }
    """.trimIndent())
  }

  fun `test teardown in finally highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class SuperTearDownInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          super.<warning descr="'tearDown()' is not called from 'finally' block">tearDown</warning>()
          System.out.println()
        }
      }      
    """.trimIndent())
  }
}