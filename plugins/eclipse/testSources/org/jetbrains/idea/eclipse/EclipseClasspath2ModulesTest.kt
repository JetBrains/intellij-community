// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class EclipseClasspath2ModulesTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @Test
  fun testAllProps() {
    doTest("eclipse-ws-3.4.1-a", "all-props")
  }

  @Test
  fun testMultiModuleDependencies() {
    doTest("multi", "m1")
  }

  @Test
  fun testRelativePaths() {
    doTest("relPaths", "scnd")
  }

  @Test
  fun testIDEA53188() {
    doTest("multi", "main")
  }

  @Test
  fun testSameNames() {
    doTest("root", "proj1")
  }

  private fun doTest(workspacePath: String, projectName: String) {
    val testDataRoot = eclipseTestDataRoot
    val testRoot = testDataRoot / "round" / StringUtil.decapitalize(testName.methodName.removePrefix("test"))
    val commonRoot = testDataRoot / "common" / "twoModulesWithClasspathStorage"
    checkLoadSaveRoundTrip(listOf(testRoot, commonRoot), tempDirectory, false, listOf(
      "test" to "$workspacePath/$projectName/$projectName",
      "ws-internals" to "$workspacePath/ws-internals/ws-internals"
    ))
  }


  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}