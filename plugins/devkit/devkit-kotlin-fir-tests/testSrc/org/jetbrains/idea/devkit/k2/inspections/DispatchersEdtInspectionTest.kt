// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.kotlin.inspections.DispatchersEdtInspection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class DispatchersEdtInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR_WITH_KOTLIN)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun enableInspection() {
    myFixture.enableInspections(DispatchersEdtInspection::class.java)
    myFixture.configureByText("coroutines.kt", /*language=kotlin*/  """
      package com.intellij.openapi.application
      import kotlin.coroutines.CoroutineContext
      import kotlinx.coroutines.*

      val Dispatchers.EDT: CoroutineContext get() = throw UnsupportedOperationException()

      val Dispatchers.UI: CoroutineContext get() = throw UnsupportedOperationException()
    """.trimIndent())
  }

  @Test
  fun `regular Dispatchers EDT`() {
    myFixture.configureByText("file.kt", /*language=kotlin*/ """
      import com.intellij.openapi.application.EDT
      import kotlinx.coroutines.*
      
      suspend fun foo() {
        withContext(Dispatchers.<weak_warning>EDT</weak_warning>) {
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  @Test
  fun `replacement with Dispatchers UI`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("file.kt", /*language=kotlin*/ """
      import com.intellij.openapi.application.EDT
      import kotlinx.coroutines.*
      
      suspend fun foo() {
        withContext(Dispatchers.<weak_warning>E<caret>DT</weak_warning>) {
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
    val intention = myFixture.getAvailableIntention("Replace 'Dispatchers.EDT' with 'Dispatchers.UI'")
    requireNotNull(intention)
    withContext(Dispatchers.EDT) {
      myFixture.checkPreviewAndLaunchAction(intention)

      myFixture.checkResult(/*language=kotlin*/ """
        import com.intellij.openapi.application.EDT
        import kotlinx.coroutines.*
        import com.intellij.openapi.application.UI
      
        suspend fun foo() {
          withContext(Dispatchers.UI) {
          }
        }
      """.trimIndent())
    }
  }
}