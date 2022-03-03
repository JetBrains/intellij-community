// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.rules.TestNameExtension
import com.intellij.util.PathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.copyTo

class EclipseClasspathTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val testName = TestNameExtension()

  @Test
  fun testAbsolutePaths() {
    doTest("parent/parent/test")
  }

  @Test
  fun testWorkspaceOnly() {
    doTest()
  }

  @Test
  fun testExportedLibs() {
    doTest()
  }

  @Test
  fun testPathVariables() {
    doTest()
  }

  @Test
  fun testJunit() {
    doTest()
  }

  @Test
  fun testSrcBinJRE() {
    doTest()
  }

  @Test
  fun testSrcBinJRESpecific() {
    doTest()
  }

  @Test
  fun testNativeLibs() {
    doTest()
  }

  @Test
  fun testAccessrulez() {
    doTest()
  }

  @Test
  fun testSrcBinJREProject() {
    doTest()
  }

  @Test
  fun testSourceFolderOutput() {
    doTest()
  }

  @Test
  fun testMultipleSourceFolders() {
    doTest()
  }

  @Test
  fun testEmptySrc() {
    doTest()
  }

  @Test
  fun testHttpJavadoc() {
    doTest()
  }

  @Test
  fun testHome() {
    doTest()
  }

  //public void testNoJava() throws Exception {
  //  doTest();

  @Test//}
  fun testNoSource() {
    doTest()
  }

  @Test
  fun testPlugin() {
    doTest()
  }

  @Test
  fun testRoot() {
    doTest()
  }

  @Test
  fun testUnknownCon() {
    doTest()
  }

  @Test
  fun testSourcesAfterAll() {
    doTest()
  }

  @Test
  fun testLinkedSrc() {
    doTest()
  }

  @Test
  fun testSrcRootsOrder() {
    doTest()
  }

  @Test
  fun testResolvedVariables() {
    doTest(setupPathVariables = true)
  }

  @Test
  fun testResolvedVars() {
    doTest("test", true, "linked")
  }

  @Test
  fun testResolvedVarsInOutput() {
    doTest("test", true, "linked")
  }

  @Test
  fun testResolvedVarsInLibImlCheck1() {
    doTest("test", true, "linked")
  }

  @Test
  fun testNoDotProject() {
    doTest(fileSuffixesToCheck = listOf("/.classpath", "/.project", ".iml"), updateExpectedDir = {
      val dotProject = eclipseTestDataRoot.resolve("round/workspaceOnly/test/.project")
      dotProject.copyTo(it.resolve("test/.project"))
    })
  }


  private fun doTest(eclipseProjectDirPath: String = "test", setupPathVariables: Boolean = false, testDataParentDir: String = "round",
                     updateExpectedDir: (Path) -> Unit = {}, fileSuffixesToCheck: List<String> = listOf("/.classpath", ".iml")) {
    val testDataRoot = eclipseTestDataRoot
    val testRoot = testDataRoot.resolve(testDataParentDir).resolve(testName.methodName.removePrefix("test").decapitalize())
    val commonRoot = testDataRoot.resolve("common").resolve("testModuleWithClasspathStorage")
    val modulePath = "$eclipseProjectDirPath/${PathUtil.getFileName(eclipseProjectDirPath)}"
    loadEditSaveAndCheck(listOf(testRoot, commonRoot), tempDirectory, setupPathVariables, listOf("test" to modulePath), ::forceSave,
                         updateExpectedDir, fileSuffixesToCheck)
  }

  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()

  }
}