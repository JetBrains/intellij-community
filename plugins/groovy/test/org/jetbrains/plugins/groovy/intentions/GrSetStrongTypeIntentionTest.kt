// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Test

@TestApplication
class GrSetStrongTypeIntentionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val grIntention by grIntentionFixture(projectFixture, "Declare explicit type", TestUtils.getAbsoluteTestDataPath() + "intentions/setStrongType/")

  @Test
  fun testUnsupportedWithTupleDeclaration() {
    grIntention.doTest(false)
  }
}