// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyMultipleMainMethodsInspectionTest : GrHighlightingTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getBasePath(): String = TestUtils.getTestDataPath() + "inspections/multipleMainMethods/"

  override fun setUp() {
    super.setUp()
    fixture.enableInspections(GroovyMultipleMainMethodsInspection::class.java)
  }

  fun testNoMethodInClass() = doTest()

  fun testSingleMethodInClass() = doTest()

  fun testMultipleMethodsInClass() = doTest()

  fun testNoMethodInScript() = doTest()

  fun testSingleMethodInScript() = doTest()

  fun testMultipleMethodsInScript() = doTest()

  fun testNoMethodInNestedClass() = doTest()

  fun testSingleMethodInNestedClass() = doTest()

  fun testMultipleMethodsInNestedClass() = doTest()

  fun testMethodInNestedAndOuterClass() = doTest()

  fun testMethodInNestedAndScriptClass() = doTest()
}