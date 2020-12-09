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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class EclipseIml2ModulesTest {
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
  fun testRelativePaths() {
    doTest("relPaths", "scnd")
  }

  private fun doTest(workspacePath: String, projectName: String) {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val testDataRoot = eclipseTestDataRoot
    val testRoot = testDataRoot / "iml" / testName.methodName.removePrefix("test").decapitalize()
    val commonRoot = testDataRoot / "common" / "twoModulesWithClasspathStorage"

    val imlFilePaths = listOf(
      "test" to "$workspacePath/$projectName/$projectName",
      "ws-internals" to "$workspacePath/ws-internals/ws-internals"
    )
    checkConvertToStandardStorage(listOf(testRoot, commonRoot), tempDirectory, testRoot / "expected" / "expected.iml",
                                  false, imlFilePaths)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}