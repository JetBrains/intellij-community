// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.PathUtil
import com.intellij.util.io.div
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class EclipseClasspathTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

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


  private fun doTest(eclipseProjectDirPath: String = "test", setupPathVariables: Boolean = false, testDataParentDir: String = "round") {
    val testDataRoot = eclipseTestDataRoot
    val testRoot = testDataRoot / testDataParentDir / testName.methodName.removePrefix("test").decapitalize()
    val commonRoot = testDataRoot / "common" / "testModuleWithClasspathStorage"
    val modulePath = "$eclipseProjectDirPath/${PathUtil.getFileName(eclipseProjectDirPath)}"
    checkLoadSaveRoundTrip(listOf(testRoot, commonRoot), tempDirectory, setupPathVariables, listOf("test" to modulePath))
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

  }
}