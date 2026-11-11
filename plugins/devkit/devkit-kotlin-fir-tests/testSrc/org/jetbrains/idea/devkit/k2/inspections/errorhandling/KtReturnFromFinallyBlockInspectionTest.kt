// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.errorhandling

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.errorhandling.ReturnFromFinallyBlockInspection
import org.junit.jupiter.api.Test

@TestApplication
class KtReturnFromFinallyBlockInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test simple return`(): Unit = doTest("""
    fun foo() {
      try {
      } finally {
        <warning descr="'return' inside 'finally' block">return</warning>
      }
    }
  """.trimIndent())

  @Test
  fun `test return from local function`(): Unit = doTest("""
    fun foo() {
      try {
      } finally {
        fun bar() {
          return
        }
      }
    }
  """.trimIndent())

  @Test
  fun `test nested try-finally in finally block`(): Unit = doTest("""
    fun foo() {
      try {
      } finally {
        try {
          <warning descr="'return' inside 'finally' block">return</warning>
        } finally {}
      }
    }
  """.trimIndent())

  @Test
  fun `test return from lambda over finally`(): Unit = doTest("""
    fun run(body: () -> Unit): Unit = body()
    
    fun foo() {
      run bar@{
        try {
        } finally {
          <warning descr="'return' inside 'finally' block">return@bar</warning>
        }
      }
    }
  """.trimIndent())

  @Test
  fun `test return from lambda in finally`(): Unit = doTest("""
    fun run(body: () -> Unit): Unit = body()
    
    fun foo() {
      try {
      } finally {
        run bar@{
          try {
              return@bar
          } finally {}
        }
      }
    }
  """.trimIndent())

  @Test
  fun `test return from lambda over finally but nested try-finally`(): Unit = doTest("""
    fun run(body: () -> Unit): Unit = body()
    
    fun foo() {
      run bar@{
        try {
        } finally {
          try {
            <warning descr="'return' inside 'finally' block">return@bar</warning>
          } finally {}
        }
      }
    }
  """.trimIndent())

  private fun doTest(code: String) {
    myFixture.enableInspections(ReturnFromFinallyBlockInspection())
    myFixture.configureByText("Foo.kt", code)
    myFixture.testHighlighting()
  }
}
