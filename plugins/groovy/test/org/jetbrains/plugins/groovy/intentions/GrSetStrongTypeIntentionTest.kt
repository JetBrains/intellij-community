// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

class GrSetStrongTypeIntentionTest: GrIntentionTestCase("Declare explicit type") {
  override fun getBasePath(): String {
    return TestUtils.getTestDataPath() + "intentions/setStrongType/"
  }

  fun testUnsupportedWithTupleDeclaration() {
    doTest(false)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }
}