// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection.deadCode

import com.intellij.junit.testFramework.JUnit5ImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnit5ImplicitUsageProviderTest : JUnit5ImplicitUsageProviderTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

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

  fun `test implicit usage of parameter in parameterized test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        fun byName(name: String) { }
      }
   """.trimIndent())
  }

  fun `test implicit usage of method source with implicit method name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.stream.*
      
      class MyTest {
        @org.junit.jupiter.params.provider.MethodSource
        @org.junit.jupiter.params.ParameterizedTest
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
}