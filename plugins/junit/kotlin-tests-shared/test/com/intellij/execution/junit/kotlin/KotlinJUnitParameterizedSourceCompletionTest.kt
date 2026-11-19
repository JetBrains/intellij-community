// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.jvmCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinJUnitParameterizedSourceCompletionTest {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(JUNIT5)
  }

  protected val myFixture by jvmCodeInsightFixture(projectFixture)

  @Test
  fun `test kotlin method source`() {
    myFixture.configureByText("Test.kt", """ 
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.MethodSource
      import java.util.stream.Stream
      import kotlin.jvm.JvmStatic
      
      class Test {
        companion object {
          @JvmStatic fun abc(): Stream<Int> = Stream.of(1, 2, 3)
        }

        @ParameterizedTest
        @MethodSource("<caret>")
        fun foo(i: Int) {}
      }
    """)
    myFixture.testCompletionVariants(myFixture.file.name, "abc")
  }

  @Test
  fun `test kotlin method source several methods`() {
    myFixture.configureByText("Test.kt", """ 
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.MethodSource
      import java.util.stream.Stream

      class Test {
        companion object {
          @JvmStatic fun aaa(): Stream<Int> = Stream.of(1)
          @JvmStatic fun bbb(): Stream<Int> = Stream.of(2)
          @JvmStatic fun ccc(): Stream<Int> = Stream.of(3)
          @JvmStatic fun ddd(): Stream<Int> = Stream.of(4)
        }

        @ParameterizedTest
        @MethodSource("<caret>")
        fun foo(i: Int) {}
      }
    """)
    myFixture.testCompletionVariants(myFixture.file.name, "aaa", "bbb", "ccc", "ddd")
  }

  @Test
  fun `test kotlin method source non-static method`() {
    myFixture.configureByText("Test.kt", """ 
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.MethodSource
      import java.util.stream.Stream

      class Test {
        companion object {
          @JvmStatic fun aaa(): Stream<Int> = Stream.of(1)
          @JvmStatic fun bbb(): Stream<Int> = Stream.of(2)
          @JvmStatic fun ccc(): Stream<Int> = Stream.of(3)
          fun ddd(): Stream<Int> = Stream.of(4) // <--- no @JvmStatic
        }

        @ParameterizedTest
        @MethodSource("<caret>")
        fun foo(i: Int) {}
      }
    """)
    myFixture.testCompletionVariants(myFixture.file.name, "aaa", "bbb", "ccc")
  }

  @Test
  fun `test kotlin field source`() {
    myFixture.configureByText("Test.kt", """ 
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class Test {
        companion object {
          val abc = listOf(1, 2, 3)
        }

        @ParameterizedTest
        @FieldSource("<caret>")
        fun foo(i: Int) {}
      }
    """)
    myFixture.testCompletionVariants(myFixture.file.name, "abc")
  }

  @Test
  fun `test kotlin field source several fields`() {
    myFixture.configureByText("Test.kt", """ 
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class Test {
        companion object {
          val aaa = listOf(1)
          val bbb = listOf(2)
          val ccc = listOf(3)
          val ddd = listOf(4)
        }

        @ParameterizedTest
        @FieldSource("<caret>")
        fun foo(i: Int) {}
      }
    """)
    myFixture.testCompletionVariants(myFixture.file.name, "aaa", "bbb", "ccc", "ddd")
  }
}