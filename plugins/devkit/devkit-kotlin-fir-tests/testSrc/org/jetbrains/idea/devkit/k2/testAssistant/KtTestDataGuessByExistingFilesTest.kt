// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.testAssistant

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.writeChild
import org.jetbrains.idea.devkit.testAssistant.TestDataFile
import org.jetbrains.idea.devkit.testAssistant.TestDataGuessByExistingFilesUtil
import org.jetbrains.idea.devkit.testAssistant.TestDataPathTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

/**
 * Test data guessing for Kotlin test methods declared with a backtick name, e.g. `` fun `test my-data`() ``,
 * where the test name is separated from the `test` prefix with a space.
 */
class KtTestDataGuessByExistingFilesTest : TestDataPathTestCase() {
  private lateinit var resourcesRoot: VirtualFile

  override fun setUp() {
    super.setUp()
    resourcesRoot = WriteAction.compute<VirtualFile, Exception> { myContentRoot.createChildDirectory(this, "resources") }
    PsiTestUtil.addSourceRoot(myFixture.module, resourcesRoot, JavaResourceRootType.RESOURCE)
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addLibrary("junit4", JavaSdkUtil.getJunit4JarPath())
  }

  fun testBacktickTestName() {
    resourcesRoot.writeChild("alias-bound-unsound.rs", "fn main() {}")
    resourcesRoot.writeChild("alias-bound-unsound.stderr", "error[E0275]")

    myFixture.configureByText("SomeKotlinTest.kt", """
      import org.junit.Test

      class SomeKotlinTest {
        @Test
        fun `test alias-bound-unsound`() {
        }
      }
    """.trimIndent())
    val testMethod = assertOneElement(myFixture.findClass("SomeKotlinTest")
                                        .findMethodsByName("test alias-bound-unsound", false).toList())

    val result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod, null)
    assertEquals(setOf("alias-bound-unsound.rs", "alias-bound-unsound.stderr"), result.map(TestDataFile::getName).toSet())
  }
}
