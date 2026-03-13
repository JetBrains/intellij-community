// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.kotlin.inspections.DispatchersEdtInspection
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DispatchersEdtInspectionTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = PROJECT_DESCRIPTOR_WITH_KOTLIN

  @Before
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
  fun `replacement with Dispatchers UI`() {
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