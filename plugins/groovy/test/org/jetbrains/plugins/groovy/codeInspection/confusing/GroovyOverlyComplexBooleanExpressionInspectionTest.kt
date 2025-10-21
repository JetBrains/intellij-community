// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyOverlyComplexBooleanExpressionInspectionTest: LightGroovyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_5_0

  override fun getBasePath(): String = TestUtils.getTestDataPath() + "/inspections/overlyComplexBooleanExpression"

  fun testComplexExpression() {
    myFixture.enableInspections(GroovyOverlyComplexBooleanExpressionInspection::class.java)
    myFixture.testHighlighting("${getTestName(false)}.groovy")
  }
}