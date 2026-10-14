// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.project.ProjectTypesProvider
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType

class TestDataProblemHighlightFilterTest : JavaCodeInsightFixtureTestCase() {
  private val filter = TestDataProblemHighlightFilter()

  private lateinit var sourceRoot: VirtualFile
  private lateinit var testDataDirectory: VirtualFile

  override fun toAddSourceRoot(): Boolean {
    return false
  }

  override fun setUp() {
    super.setUp()
    val contentRoot = VfsUtil.createDirectoryIfMissing(this.myFixture.tempDirPath)!!
    this.sourceRoot = VfsTestUtil.createDir(contentRoot, "src")
    this.testDataDirectory = VfsTestUtil.createDir(contentRoot, "testData")
    PsiTestUtil.addSourceRoot(this.module, this.sourceRoot)
  }

  fun testTestDataFilesStayQuietInIntelliJProject() {
    this.markAsIntelliJProject()
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "b.kt", "fun main() {}"))
  }

  fun testRegisteredFiltersStopTestDataFileInIntelliJProject() {
    this.markAsIntelliJProject()
    val file = VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main")
    runReadActionBlocking {
      val psiFile = PsiManager.getInstance(this.project).findFile(file)!!
      assertFalse(ProblemHighlightFilter.shouldHighlightFile(psiFile))
      assertFalse(ProblemHighlightFilter.shouldProcessFileInBatch(psiFile))
    }
  }

  fun testDeepTestDataFileStaysQuietInIntelliJProject() {
    this.markAsIntelliJProject()
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "one/two/three/four/deep.go", "package main"))
  }

  fun testSourceFilesStayHighlightedInIntelliJProject() {
    this.markAsIntelliJProject()
    this.assertHighlighted(true, VfsTestUtil.createFile(this.sourceRoot, "a.kt", "fun main() {}"))
    this.assertHighlighted(true, VfsTestUtil.createFile(this.sourceRoot, "testData/inSource.go", "package main"))
  }

  fun testTestDataFilesStayHighlightedInOtherProject() {
    this.assertHighlighted(true, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
    this.assertHighlighted(true, VfsTestUtil.createFile(this.testDataDirectory, "b.kt", "fun main() {}"))
  }

  fun testTestDataFilesStayQuietInPluginProject() {
    this.markAsIntelliJPluginProject()
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
  }

  fun testPluginProjectIsRecognizedAfterACheckDuringIndexing() {
    this.markAsIntelliJPluginProject()
    val file = VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main")
    DumbModeTestUtils.runInDumbModeSynchronously(this.project) {
      this.assertHighlighted(true, file)
    }
    this.assertHighlighted(false, file)
  }

  fun testTestDataInTestResourceRootStaysQuiet() {
    this.markAsIntelliJProject()
    PsiTestUtil.addSourceRoot(this.module, this.testDataDirectory, JavaResourceRootType.TEST_RESOURCE)
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
  }

  fun testTestDataInExcludedDirectoryStaysQuiet() {
    this.markAsIntelliJProject()
    PsiTestUtil.addExcludedRoot(this.module, this.testDataDirectory)
    this.assertHighlighted(false, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
  }

  fun testTestDataInTestSourceRootStaysHighlighted() {
    this.markAsIntelliJProject()
    PsiTestUtil.addSourceRoot(this.module, this.testDataDirectory, true)
    this.assertHighlighted(true, VfsTestUtil.createFile(this.testDataDirectory, "a.go", "package main"))
  }

  fun testDirectoryNamesThatOnlyContainTestDataStayHighlighted() {
    this.markAsIntelliJProject()
    val contentRoot = this.testDataDirectory.parent
    this.assertHighlighted(true, VfsTestUtil.createFile(contentRoot, "update-testdata/a.go", "package main"))
    this.assertHighlighted(true, VfsTestUtil.createFile(contentRoot, "regression-testData/b.go", "package main"))
  }

  fun testProjectBelowTestDataDirectoryStaysHighlighted() {
    this.markAsIntelliJProject()
    val checkout = VfsTestUtil.createDir(this.testDataDirectory, "checkout")
    val checkoutModule = PsiTestUtil.addModule(this.project, JavaModuleType.getModuleType(), "checkout", checkout)
    PsiTestUtil.removeSourceRoot(checkoutModule, checkout)
    this.assertHighlighted(true, VfsTestUtil.createFile(checkout, "tools/build.go", "package main"))
    this.assertHighlighted(false, VfsTestUtil.createFile(checkout, "testData/inside.go", "package main"))
  }

  fun testContentRootNamedTestDataStaysQuiet() {
    this.markAsIntelliJProject()
    val contentRoot = VfsTestUtil.createDir(VfsTestUtil.createDir(this.testDataDirectory.parent, "tests"), "testData")
    val testDataModule = PsiTestUtil.addModule(this.project, JavaModuleType.getModuleType(), "testDataModule", contentRoot)
    PsiTestUtil.removeSourceRoot(testDataModule, contentRoot)
    this.assertHighlighted(false, VfsTestUtil.createFile(contentRoot, "a.go", "package main"))
  }

  private fun markAsIntelliJProject() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(this.project, true)
  }

  private fun markAsIntelliJPluginProject() {
    val provider = ProjectTypesProvider { listOf(ProjectType("INTELLIJ_PLUGIN")) }
    ProjectTypesProvider.EP_NAME.point.registerExtension(provider, this.testRootDisposable)
  }

  private fun assertHighlighted(expected: Boolean, file: VirtualFile) {
    runReadActionBlocking {
      val psiFile = PsiManager.getInstance(this.project).findFile(file)
      assertNotNull("No PSI file for " + file.path, psiFile)
      assertEquals("shouldHighlight for " + file.path, expected, this.filter.shouldHighlight(psiFile!!))
      assertEquals("shouldProcessInBatch for " + file.path, expected, this.filter.shouldProcessInBatch(psiFile))
    }
  }
}
