// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.jvm.analysis.testFramework.JvmLanguage

abstract class KotlinJUnitMalformedDeclarationInspectionTestV6 : KotlinJUnitMalformedDeclarationInspectionTestBase(JUnitLibrary.JUNIT6) {
  /* Unlike in JUnit 5, in JUnit 6 suspend functions are allowed for tests */
  fun `test suspending test method`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
          import org.junit.jupiter.api.Test
        
          class JUnit6Test {
            @Test
            suspend fun testFoo() { }
          }    
    """.trimIndent())
  }

  fun `test suspending parameterized test method`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
          import org.junit.jupiter.params.ParameterizedTest
          import org.junit.jupiter.params.provider.ValueSource
        
          class JUnit6Test {
            @ParameterizedTest
            @ValueSource(ints = [1])
            suspend fun testWithIntValues(i: Int) { }
          }
    """.trimIndent())
  }

  fun `test suspending test method with @TempDir parameter`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
          import org.junit.jupiter.api.Test
          import org.junit.jupiter.api.io.TempDir
          import java.io.File
        
          class JUnit6Test {
            @Test
            suspend fun testFoo(@TempDir tempFolder: File) { }
          }    
    """.trimIndent())
  }

  fun `test suspending lifecycle methods`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
          import org.junit.jupiter.api.*
        
          class JUnit6Test {
            @BeforeEach
            suspend fun beforeEach() { }
            
            @AfterEach
            suspend fun afterEach() { }
            
            companion object {
              @BeforeAll
              @JvmStatic
              suspend fun beforeAll() { }
              
              @AfterAll
              @JvmStatic
              suspend fun afterAll() { }
            }
          }    
    """.trimIndent())
  }
}