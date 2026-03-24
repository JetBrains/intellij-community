// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin


abstract class KotlinJUnitTestRunLineMarkerTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
  companion object {
    val descriptor: JUnitProjectDescriptor = JUnitProjectDescriptor(HIGHEST, MavenTestLib.JUNIT5)
  }

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

  override fun getProjectDescriptor() = descriptor

  fun testCustomAnnotation() {
    myFixture.configureByText("MySuperTest.kt", """
      @Retention(AnnotationRetention.RUNTIME)
      @Target(AnnotationTarget.FUNCTION)
      @org.junit.jupiter.api.Test
      annotation class MySuperTest
      
      """.trimIndent())
    myFixture.configureByText("MyKTest.kt", """
      class MyKTest {
          @MySuperTest
          fun test<caret>1() {}
          @org.junit.jupiter.api.Test
          fun test2() {}
      }
      
      """.trimIndent())
    val marks = myFixture.findGuttersAtCaret().filter { it.getTooltipText() == "Run Test" }
    TestCase.assertEquals(1, marks.size)
  }
}