// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
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
}