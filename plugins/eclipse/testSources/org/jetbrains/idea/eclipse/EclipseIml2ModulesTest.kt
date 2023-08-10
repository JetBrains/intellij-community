// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.div

@TestApplication
class EclipseIml2ModulesTest {
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
  fun testRelativePaths() {
    doTest("relPaths", "scnd")
  }

  private fun doTest(workspacePath: String, projectName: String) {
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
}