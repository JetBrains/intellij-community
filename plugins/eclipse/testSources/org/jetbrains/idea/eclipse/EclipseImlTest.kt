// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class EclipseImlTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @Test
  fun testWorkspaceOnly() {
    doTest()
  }

  @Test
  fun testSrcBinJREProject() {
    doTest()
  }

  @Test
  fun testEmptySrc() {
    doTest()
  }

  @Test
  fun testEmpty() {
    doTest()
  }

  @Test
  fun testRoot() {
    doTest()
  }

  @Test
  fun testResolvedGlobalLibrary() {
    projectModel.addApplicationLevelLibrary("globalLib")
    doTest()
  }

  @Test
  fun testUnresolvedUserLibrary() {
    doTest()
  }

  @Test
  fun testResolvedVariables() {
    doTest(setupPathVariables = true)
  }

  @Test
  fun testResolvedVarsInIml() {
    doTest(true, "linked")
  }

  @Test
  fun testResolvedVarsInOutputImlCheck() {
    doTest(true, "linked")
  }

  @Test
  fun testResolvedVarsInLibImlCheck() {
    doTest(true, "linked")
  }


  private fun doTest(setupPathVariables: Boolean = false, testDataDir: String = "iml") {
    val testDataRoot = eclipseTestDataRoot
    val testRoot = testDataRoot.resolve(testDataDir).resolve(testName.methodName.removePrefix("test").decapitalize())
    val commonRoot = testDataRoot.resolve("common").resolve("testModuleWithClasspathStorage")

    checkConvertToStandardStorage(listOf(testRoot, commonRoot), tempDirectory, testRoot.resolve("expected").resolve("expected.iml"),
                                  setupPathVariables, listOf("test" to "test/test"))
  }

  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()
  }
}