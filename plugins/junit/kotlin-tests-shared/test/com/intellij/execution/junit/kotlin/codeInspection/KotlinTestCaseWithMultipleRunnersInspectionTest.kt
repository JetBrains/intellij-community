// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.execution.junit.kotlin.kotlinJUnitProjectFixture
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.testCaseWithMultipleRunnersInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinTestCaseWithMultipleRunnersInspectionTest {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(JUNIT4)
  }

  protected val myFixture by testCaseWithMultipleRunnersInspectionFixture(projectFixture)

  @Test
  fun `test parent annotation`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      public class ParentTestBaseSuite {
      }
    """.trimIndent())
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in ParentTestBaseSuite class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : ParentTestBaseSuite() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  @Test
  fun `test interface annotation`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Suite::class)
      @org.junit.runners.Suite.SuiteClasses(Object::class)
      interface ParentTestBaseSuite {
      }

      @org.junit.runner.<warning descr="@RunWith annotation already exists in ParentTestBaseSuite class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : ParentTestBaseSuite { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  @Test
  fun `test inherited annotation1`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Suite::class)
      @org.junit.runners.Suite.SuiteClasses(Object::class)
      interface SecondParentSuite {
      }

      interface FirstParent : SecondParentSuite {
      }

      interface DummyInterface {
      }
      
      @org.junit.runner.<warning descr="@RunWith annotation already exists in SecondParentSuite class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : DummyInterface, FirstParent { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  @Test
  fun `test inherited annotation2`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Suite::class)
      @org.junit.runners.Suite.SuiteClasses(Object::class)
      open class SecondParent {
      }
      
      open class FirstParent : SecondParent() {
      }
      
      @org.junit.runner.<warning descr="@RunWith annotation already exists in SecondParent class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : FirstParent() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  @Test
  fun `test not inherited annotation`() {
    myFixture.addClass("""
      public class ParentTestBase {
      }
    """.trimIndent())
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized::class)
      class MyTest : ParentTestBase() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }
}