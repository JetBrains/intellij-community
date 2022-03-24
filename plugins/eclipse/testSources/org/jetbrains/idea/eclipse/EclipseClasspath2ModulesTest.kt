// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class EclipseClasspath2ModulesTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

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
    @RegisterExtension
    val appRule = ApplicationExtension()
  }
}