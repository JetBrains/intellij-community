// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections.errorhandling

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.errorhandling.ReturnFromFinallyBlockInspection

class KtReturnFromFinallyBlockInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test simple return`() = doTest("""
    fun foo() {
      try {
      } finally {
        <warning descr="'return' inside 'finally' block">return</warning>
      }
    }
  """.trimIndent())

  fun `test return from local function`() = doTest("""
    fun foo() {
      try {
      } finally {
        fun bar() {
          return
        }
      }
    }
  """.trimIndent())

  fun `test nested try-finally in finally block`() = doTest("""
    fun foo() {
      try {
      } finally {
        try {
          <warning descr="'return' inside 'finally' block">return</warning>
        } finally {}
      }
    }
  """.trimIndent())

  fun `test return from lambda over finally`() = doTest("""
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

  fun `test return from lambda in finally`() = doTest("""
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

  fun `test return from lambda over finally but nested try-finally`() = doTest("""
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
