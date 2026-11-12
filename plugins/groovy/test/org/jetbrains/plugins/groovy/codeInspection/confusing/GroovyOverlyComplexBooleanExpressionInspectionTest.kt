// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Test

@TestApplication
class GroovyOverlyComplexBooleanExpressionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val myFixture by lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "/inspections/overlyComplexBooleanExpression")

  @Test
  fun testComplexExpression() {
    myFixture.enableInspections(GroovyOverlyComplexBooleanExpressionInspection::class.java)
    myFixture.testHighlighting("${myFixture.getTestName(false)}.groovy")
  }
}