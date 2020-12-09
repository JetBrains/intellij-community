// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class EclipseImlTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @JvmField
  @Rule
  val projectModel = ProjectModelRule()

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
    @ClassRule
    val appRule = ApplicationRule()
  }
}