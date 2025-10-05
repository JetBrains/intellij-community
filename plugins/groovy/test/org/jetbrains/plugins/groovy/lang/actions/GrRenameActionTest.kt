// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions

import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GrRenameActionTest : LightGroovyTestCase() {
  override fun getBasePath(): @NonNls String {
    return TestUtils.getTestDataPath() + "rename"
  }

  fun testPatternVariableSimple() = doTest("newB")

  fun testPatternVariableAfter() = doTest("newB")

  private fun doTest(newName: String) {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/before.groovy")
    myFixture.renameElementAtCaret(newName)
    myFixture.checkResultByFile("$testName/after.groovy")
  }
}