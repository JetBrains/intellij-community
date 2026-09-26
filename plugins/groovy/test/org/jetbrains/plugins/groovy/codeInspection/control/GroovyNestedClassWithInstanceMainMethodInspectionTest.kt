// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyNestedClassWithInstanceMainMethodInspectionTest: GrHighlightingTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getBasePath(): String = TestUtils.getTestDataPath() + "inspections/nestedInstanceMainMethod/"

  override fun setUp() {
    super.setUp()
    fixture.enableInspections(GroovyNestedClassWithInstanceMainMethodInspection::class.java)
  }

  fun testNestedClass() = doTest()

  fun testNestedClassWithStatic() = doTest()
}