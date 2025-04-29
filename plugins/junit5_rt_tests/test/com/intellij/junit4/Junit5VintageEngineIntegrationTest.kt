// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

class Junit5VintageEngineIntegrationTest : AbstractTestFrameworkCompilingIntegrationTest() {
  override fun setupModule() {
    super.setupModule()
    val repoManager = getRepoManager()
    addMavenLibs(myModule, JpsMavenRepositoryLibraryDescriptor("org.junit.vintage", "junit-vintage-engine", "5.10.1"), repoManager)
  }
  override fun getTestContentRoot(): String? = VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/vintageEngine")

  fun testRunPackageJUnit4() {
    val configuration = createRunPackageConfiguration("v4")
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  fun testRunClassJUnit4() {
    val testClass = findClass("v4")
    val configuration = createConfiguration<RunConfiguration>(testClass)
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  fun testRunMethodJUnit4() {
    val testClass = findClass("v4")
    val testMethod = testClass.findMethodsByName("simple", false)[0]
    val configuration = createConfiguration<RunConfiguration>(testMethod)
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  fun testRunPackageJUnit3() {
    val configuration = createRunPackageConfiguration("v3")
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  fun testRunClassJUnit3() {
    val testClass = findClass("v3")
    val configuration = createConfiguration<RunConfiguration>(testClass)
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  fun testRunMethodJUnit3() {
    val testClass = findClass("v3")
    val testMethod = testClass.findMethodsByName("testSimple", false)[0]
    val configuration = createConfiguration<RunConfiguration>(testMethod)
    val processOutput = doStartTestsProcess(configuration)
    assertTrue(processOutput.sys.toString().contains("-junit5"))
  }

  private fun createRunPackageConfiguration(packageName: String): RunConfiguration {
    val aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName)
    assertNotNull(aPackage)
    val configuration = createConfiguration<RunConfiguration?>(aPackage!!)
    assertInstanceOf(configuration, JUnitConfiguration::class.java)
    return configuration!!
  }

  private fun findClass(packageName:String ): PsiClass {
    val testClass = JavaPsiFacade.getInstance(myProject).findClass("${packageName}.TestClass", GlobalSearchScope.projectScope(myProject))
    assertNotNull(testClass)
    return testClass!!
  }
}