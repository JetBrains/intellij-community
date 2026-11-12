// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Test

@TestApplication
class GrRenameActionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1)
  }

  private val myFixture by lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "rename")

  @Test
  fun testPatternVariableSimple(): Unit = doTest("newB")

  @Test
  fun testPatternVariableAfter(): Unit = doTest("newB")

  private fun doTest(newName: String): Unit = timeoutRunBlocking {
    val testName = myFixture.getTestName(true)
    myFixture.configureByFile("$testName/before.groovy")
    withContext(Dispatchers.EDT) {
      myFixture.renameElementAtCaret(newName)
    }
    myFixture.checkResultByFile("$testName/after.groovy")
  }
}