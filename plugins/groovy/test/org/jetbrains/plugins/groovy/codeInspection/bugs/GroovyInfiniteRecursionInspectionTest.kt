// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyInfiniteRecursionInspectionTest: LightGroovyTestCase() {
  override fun getBasePath(): String {
    return TestUtils.getTestDataPath() + "inspections/infiniteRecursion"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }


  fun testRecursion() {
    myFixture.enableInspections(GroovyInfiniteRecursionInspection::class.java)
    myFixture.testHighlighting("${getTestName(false)}.groovy")
  }
}