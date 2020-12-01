// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.copy
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@ExperimentalPathApi
class EclipseEmlTest {
  @JvmField
  @Rule
  val tempDirectory = TempDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @Test
  fun testSrcInZip() {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    doTest()
  }

  @Test
  fun testPreserveInheritedInvalidJdk() {
    doTest({ ModuleRootModificationUtil.setSdkInherited(ModuleManager.getInstance(it).modules.single()) },
           {
             val from = eclipseTestDataRoot / "eml" / "preserveInheritedInvalidJdk" / "test" / "expected" / "preserveInheritedInvalidJdk.eml"
             from.copy(it / "test" / "preserveInheritedInvalidJdk.eml")
           })
  }

  private fun doTest(edit: (Project) -> Unit = {}, updateExpectedDir: (Path) -> Unit = {}) {
    val testName = testName.methodName.removePrefix("test").decapitalize()
    val testRoot = eclipseTestDataRoot / "eml" / testName
    val commonRoot = eclipseTestDataRoot / "common" / "testModuleWithClasspathStorage"
    checkEmlFileGeneration(listOf(testRoot, commonRoot), tempDirectory, listOf("test" to "test/$testName"), edit, updateExpectedDir)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

}