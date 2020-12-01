// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class EclipseEml2ModulesTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @Test
  fun testSourceRootPaths() {
    doTest("ws-internals")
  }

  @Test
  fun testAnotherSourceRootPaths() {
    doTest("anotherPath")
  }

  private fun doTest(secondRootName: String) {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
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
    @ClassRule
    val appRule = ApplicationRule()
  }
}