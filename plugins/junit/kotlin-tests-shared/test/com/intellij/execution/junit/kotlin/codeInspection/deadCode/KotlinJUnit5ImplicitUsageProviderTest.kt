// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection.deadCode

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.execution.junit.kotlin.kotlinJUnitProjectFixture
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinJUnit5ImplicitUsageProviderTest(
  inspection: () -> InspectionProfileEntry = { UnusedDeclarationInspection(true) },
) {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(JUNIT5)
  }

  protected val myFixture by jvmInspectionFixture(projectFixture, inspection = inspection)

  @Test
  fun `test implicit usage of enum source`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
        enum class HostnameVerification {
          YES, NO
        }
        
        @org.junit.jupiter.params.provider.EnumSource(HostnameVerification::class)
        @org.junit.jupiter.params.ParameterizedTest
        fun testHostNameVerification(hostnameVerification: HostnameVerification) {
          System.out.println(hostnameVerification)
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of parameter in parameterized test`() {
    // KotlinHighlightVisitor ignores parameter checking for EntryPoints.
    // @see org.jetbrains.kotlin.checkers.KotlinHighlightVisitorCustomTest#testNoUnusedParameterWhenCustom()
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        class MyTest {
          @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
          fun byName(name: String, <warning descr="Parameter \"flag\" is never used">flag</warning>: Boolean = false) { }
        }
     """.trimIndent())
  }

  @Test
  fun `test implicit usage of multiple parameters in parameterized test`() {
    // KotlinHighlightVisitor ignores parameter checking for EntryPoints.
    // @see org.jetbrains.kotlin.checkers.KotlinHighlightVisitorCustomTest#testNoUnusedParameterWhenCustom()
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.provider.Arguments
      import java.util.stream.Stream
      
      class MyTest {
        companion object {
          @JvmStatic
          fun source(): Stream<Arguments> = Stream.of(
            Arguments.of("a", 1, true),
            Arguments.of("b", 2, false)
          )
        }
        
        @org.junit.jupiter.params.ParameterizedTest(name = "{0} vs {1}")
        @org.junit.jupiter.params.provider.MethodSource("source")
        fun testMultiple(name: String, value: Int, <warning descr="Parameter \"flag\" is never used">flag</warning>: Boolean) {
        }
      }
   """.trimIndent())
  }

  @Test
  fun `test unused parameter when not in display name`() {
    // KotlinHighlightVisitor ignores parameter checking for EntryPoints.
    // @see org.jetbrains.kotlin.checkers.KotlinHighlightVisitorCustomTest#testNoUnusedParameterWhenCustom()
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["a"])
        fun test(<warning descr="Parameter \"unused\" is never used">unused</warning>: String) {
        }
      }
   """.trimIndent())
  }

  @Test
  fun `test implicit usage of method source with implicit method name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.stream.*
      
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource
        fun foo(input: String) {
          System.out.println(input)
        }
        
        companion object {
          @JvmStatic
          private fun foo(): Stream<String> {
              return Stream.of("")
          }      
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test usage of method source with method name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.stream.*
      
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("bar")
        fun foo(input: String) {
          System.out.println(input)
        }
        
        companion object {
          @JvmStatic
          private fun <warning descr="Function \"foo\" is never used">foo</warning>() = Stream.of("")
          
          @JvmStatic
          private fun bar() = Stream.of("")
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of method in parameterized test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
    class MyTest {
        class NewInnerTest: InnerTest() {
            companion object {
              @JvmStatic
              private fun test() = arrayOf("NewInner")
            }
        }
    
        open class InnerTest {
            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.MethodSource("test")
            fun myTest(param: String) { System.out.println(param) }
            companion object {
              @JvmStatic
              private fun test() = arrayOf("Inner")
            }
        }
    }
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of field in parameterized test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
    class MyTest {
        class NewInnerTest: InnerTest() {
            companion object {
              private val test = arrayOf("InnerTest")
            }
        }
    
        open class InnerTest {
            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.FieldSource("test")
            fun myTest(param: String) { System.out.println(param) }
            companion object {
              private val test = arrayOf("Inner")
            }
        }
    }
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of field source with implicit field name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class MyTest {
        @ParameterizedTest
        @FieldSource
        fun foo(input: String) {
          println(input)
        }

        companion object {
          @JvmStatic
          val foo = listOf("a", "b")
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test usage of field source with field name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class MyTest {
        @ParameterizedTest
        @FieldSource("bar")
        fun foo(input: String) {
          println(input)
        }

        companion object {
          @JvmStatic
          val <warning descr="Property \"foo\" is never used">foo</warning> = listOf("a", "b")
          @JvmStatic
          val bar = listOf("a", "b")
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of TempDir as direct annotation`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
          @org.junit.jupiter.api.io.TempDir
          private lateinit var tempDir: java.nio.file.Path
            
          @org.junit.jupiter.api.Test
          fun test() { 
            System.out.println(tempDir) 
          }
      }
      
    """.trimIndent())
  }

  @Test
  fun `test implicit usage of TempDir as meta annotation`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
          @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
          @Retention(AnnotationRetention.RUNTIME)
          @org.junit.jupiter.api.io.TempDir
          annotation class CustomTempDir { }
          
          @CustomTempDir
          private lateinit var tempDir: java.nio.file.Path
            
          @org.junit.jupiter.api.Test
          fun test() { 
            System.out.println(tempDir) 
          }
      }
      
    """.trimIndent())
  }

  @Test
  fun `test malformed nested should be used highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
        import org.junit.jupiter.api.Nested
        import org.junit.jupiter.api.Test
        
        class RootTest {
            @Test fun test1() {}
        
            @Nested
            inner class NestedTestsImpl : NestedTests()
        }
        
        abstract class NestedTests {
            @Test fun test2() {}
        
            @Nested
            inner class DoubleNestedTestsImpl : DoubleNestedTests()
        }
        
        abstract class DoubleNestedTests {
            @Test fun test3() {}
        }
        """.trimIndent())
  }
}