// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtilTestCase.TestJdkProvider
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.externalSystem.util.environment.TestEnvironment
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkTestCase
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.Companion.assertSdk
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdk
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.testFramework.util.buildSettings
import org.jetbrains.plugins.gradle.testFramework.util.buildscript
import org.jetbrains.plugins.gradle.util.isSupported
import org.jetbrains.plugins.gradle.util.waitForProjectReload

abstract class GradleProjectResolverTestCase : GradleImportingTestCase() {

  val environment get() = Environment.getInstance() as TestEnvironment

  override fun setUp() {
    super.setUp()

    TestSdkGenerator.reset()

    val application = ApplicationManager.getApplication()
    application.replaceService(Environment::class.java, TestEnvironment(), testRootDisposable)
    application.replaceService(ExternalSystemJdkProvider::class.java, TestJdkProvider(), testRootDisposable)

    setRegistryPropertyForTest("unknown.sdk.auto", "false")
    setRegistryPropertyForTest("use.jdk.vendor.in.suggested.jdk.name", "false") //we have inconsistency between SDK names in JDK

    SdkType.EP_NAME.point.registerExtension(SdkTestCase.TestSdkType, testRootDisposable)

    environment.variables(ExternalSystemJdkUtil.JAVA_HOME to null)

    TestUnknownSdkResolver.unknownSdkFixMode = TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_LOCAL_FIX
  }

  fun loadProject() {
    waitForProjectReload {
      linkAndRefreshGradleProject(projectPath, myProject)
    }
  }

  fun reloadProject() {
    waitForProjectReload {
      val importSpec = ImportSpecBuilder(myProject, externalSystemId)
      ExternalSystemUtil.refreshProject(projectPath, importSpec)
    }
  }

  fun findRealTestSdk(): TestSdk? {
    val jdkType = JavaSdk.getInstance()
    val jdkInfo = jdkType.suggestHomePaths().asSequence()
      .map { createSdkInfo(jdkType, it) }
      .filter { ExternalSystemJdkUtil.isValidJdk(it.homePath) }
      .filter { isSupported(currentGradleVersion, it.versionString) }
      .firstOrNull()
    if (jdkInfo == null) {
      LOG.warn("Cannot find test JDK for Gradle $currentGradleVersion")
      return null
    }
    return TestSdkGenerator.createTestSdk(jdkInfo)
  }

  private fun createSdkInfo(sdkType: SdkType, homePath: String): TestSdkGenerator.SdkInfo {
    val name = sdkType.suggestSdkName(null, homePath)
    val versionString = sdkType.getVersionString(homePath)!!
    return TestSdkGenerator.SdkInfo(name, versionString, homePath)
  }

  fun assertSdks(sdk: TestSdk?, vararg moduleNames: String, isAssertSdkName: Boolean = true) {
    assertProjectSdk(sdk, isAssertSdkName)
    for (moduleName in moduleNames) {
      assertModuleSdk(moduleName, sdk, isAssertSdkName)
    }
  }

  private fun assertProjectSdk(sdk: TestSdk?, isAssertSdkName: Boolean) {
    val projectSdk = getSdkForProject()
    assertSdk(sdk, projectSdk, isAssertSdkName)
  }

  private fun assertModuleSdk(moduleName: String, sdk: TestSdk?, isAssertSdkName: Boolean) {
    val moduleSdk = getSdkForModule(moduleName)
    assertSdk(sdk, moduleSdk, isAssertSdkName)
  }

  private fun getSdkForProject(): Sdk? {
    return ProjectRootManager.getInstance(myProject).projectSdk
  }

  private fun getSdkForModule(moduleName: String): Sdk? {
    return ModuleRootManager.getInstance(getModule(moduleName)).sdk
  }

  private fun setProjectSdk(sdk: Sdk?) {
    val projectRootManager = ProjectRootManager.getInstance(myProject)
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        projectRootManager.projectSdk = sdk
      }
    }
  }

  fun withProjectSdk(sdk: TestSdk, action: () -> Unit) {
    val projectRootManager = ProjectRootManager.getInstance(myProject)
    val projectSdk = projectRootManager.projectSdk
    setProjectSdk(sdk)
    try {
      action()
    }
    finally {
      setProjectSdk(projectSdk)
    }
  }


  fun createGradleSubProject() {
    createProjectSubFile("settings.gradle", buildSettings { setProjectName("project") })
    createProjectSubFile("build.gradle", buildscript { withJavaPlugin() })
  }
}
