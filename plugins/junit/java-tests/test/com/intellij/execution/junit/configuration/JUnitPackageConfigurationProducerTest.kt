// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.junit.AllInPackageConfigurationProducer
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.utils.vfs.createDirectory

class JUnitPackageConfigurationProducerTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    runWriteAction {
      setupModule(MODULE_1, withTestDependency = true)
      myFixture.addFileToProject("$MODULE_1/test/foo/bar/FooTest.java", """
        package foo.bar;
        
        class FooTest { }
      """.trimIndent())

      setupModule(MODULE_2, withTestDependency = false)
      myFixture.addFileToProject("$MODULE_2/test/baz/BazTest.java", """
        package baz;
        
        class BazTest { }
      """.trimIndent())
    }
  }

  private fun setupModule(name: String, withTestDependency: Boolean): VirtualFile {
    val moduleDir = myFixture.getTempDirFixture().findOrCreateDir(name)
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, moduleDir)
    PsiTestUtil.addSourceRoot(module, moduleDir.createDirectory("test"), true)
    if (withTestDependency) {
      PsiTestUtil.addProjectLibrary(module, "JUnit4", IntelliJProjectConfiguration.Companion.getProjectLibraryClassesRootPaths("JUnit4"))
    }
    return moduleDir
  }

  private fun isApplicableDir(directory: VirtualFile): Boolean {
    val psiDirectory = PsiManager.getInstance(project).findDirectory(directory) ?: error("No directory for $directory")
    return isApplicable(psiDirectory)
  }

  private fun isApplicable(psiElement: PsiElement): Boolean {
    return AllInPackageConfigurationProducer().setupConfigurationFromContext(
      JUnitConfiguration("", project),
      ConfigurationContext(psiElement),
      Ref(psiElement)
    )
  }

  fun `test configuration is available when JUnit is present for directory`() {
    val subModuleRootDir = myFixture.getTempDirFixture().findOrCreateDir(MODULE_1)
    assertTrue(isApplicableDir(subModuleRootDir))
  }

  fun `test configuration is available when JUnit is present for package`() {
    val fooBarPkg = JavaPsiFacade.getInstance(project).findPackage("foo.bar") ?: error("Package not found 'foo.bar'")
    assertTrue(isApplicable(fooBarPkg))

    val fooPkg = JavaPsiFacade.getInstance(project).findPackage("foo") ?: error("Package not found 'foo")
    assertTrue(isApplicable(fooPkg))
  }

  fun `test configuration is not available when JUnit is not present for directory`() {
    val rootDir = myFixture.tempDirFixture.findOrCreateDir("")
    assertFalse(isApplicableDir(rootDir))
  }

  fun `test configuration is not available when JUnit is not present for package`() {
    val subModuleRootDir = JavaPsiFacade.getInstance(project).findPackage("baz") ?: error("Package not found 'baz'")
    assertFalse(isApplicable(subModuleRootDir))
  }

  fun `test configuration is available when JUnit is not present for module in dumb mode for directory`() {
    val rootDir = myFixture.tempDirFixture.findOrCreateDir("")
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      // In dumb mode we can't check module dependencies, so we check whether the dependency is in the project
      assertTrue(isApplicableDir(rootDir))
    }
  }

  private companion object {
    private const val MODULE_1 = "module1"

    private const val MODULE_2 = "module2"
  }
}