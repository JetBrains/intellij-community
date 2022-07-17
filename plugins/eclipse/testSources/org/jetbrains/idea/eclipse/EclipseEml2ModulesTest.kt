// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class EclipseEml2ModulesTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

  @Test
  fun testSourceRootPaths() {
    doTest("ws-internals")
  }

  @Test
  fun testAnotherSourceRootPaths() {
    doTest("anotherPath")
  }

  private fun doTest(secondRootName: String) {
    val testName = testName.methodName.removePrefix("test").decapitalize()
    val testRoot = eclipseTestDataRoot.resolve("eml").resolve(testName)
    val commonRoot = eclipseTestDataRoot.resolve("common").resolve("twoModulesWithClasspathStorage")
    checkEmlFileGeneration(listOf(testRoot, commonRoot), tempDirectory, listOf(
      "test" to "srcPath/sourceRootPaths/sourceRootPaths",
      "ws-internals" to "srcPath/$secondRootName/ws-internals"
    ))
  }

  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()
  }
}