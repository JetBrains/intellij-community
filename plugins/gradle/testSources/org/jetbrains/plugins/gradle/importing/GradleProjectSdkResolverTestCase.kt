// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing


import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleProjectConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction

abstract class GradleProjectSdkResolverTestCase : GradleImportingTestCase() {

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

  override fun installGradleJvmConfigurator() {
    // The Gradle JVM is configured during the test execution by the [resolveRealTestSdk] function
  }

  suspend fun loadProject() {
    awaitGradleProjectConfiguration(myProject) {
      linkAndSyncGradleProject(myProject, projectPath)
    }
  }

  suspend fun reloadProject() {
    awaitGradleProjectConfiguration(myProject) {
      val importSpec = ImportSpecBuilder(myProject, externalSystemId)
      ExternalSystemUtil.refreshProject(projectPath, importSpec)
    }
  }

  fun resolveRealTestSdk(): Sdk {
    val homePath = GradleJvmResolver.resolveGradleJvmHomePath(currentGradleVersion, JavaVersionRestriction.NO)
    val sdkInfo = createSdkInfo(JavaSdk.getInstance(), homePath)
    return TestSdkGenerator.createTestSdk(sdkInfo)
  }

  private fun createSdkInfo(sdkType: SdkType, homePath: String): TestSdkGenerator.SdkInfo {
    val name = sdkType.suggestSdkName(null, homePath)
    val versionString = sdkType.getVersionString(homePath)!!
    return TestSdkGenerator.SdkInfo(name, versionString, homePath)
  }

  fun assertSdks(sdk: Sdk?, vararg moduleNames: String, isAssertSdkName: Boolean = true) {
    assertProjectSdk(sdk, isAssertSdkName)
    for (moduleName in moduleNames) {
      assertModuleSdk(moduleName, sdk, isAssertSdkName)
    }
  }

  private fun assertProjectSdk(sdk: Sdk?, isAssertSdkName: Boolean) {
    val projectSdk = getSdkForProject()
    assertSdk(sdk, projectSdk, isAssertSdkName)
  }

  private fun assertModuleSdk(moduleName: String, sdk: Sdk?, isAssertSdkName: Boolean) {
    val moduleSdk = getSdkForModule(moduleName)
    assertSdk(sdk, moduleSdk, isAssertSdkName)
  }

  private fun getSdkForProject(): Sdk? {
    return ProjectRootManager.getInstance(myProject).projectSdk
  }

  private fun getSdkForModule(moduleName: String): Sdk? {
    return ModuleRootManager.getInstance(getModule(moduleName)).sdk
  }

  protected inline fun withProjectSdk(sdk: Sdk, action: () -> Unit) {
    SdkTestCase.withProjectSdk(myProject, sdk, action)
  }

  fun createGradleSubProject() {
    createSettingsFile {
      setProjectName("project")
    }
    createBuildFile {
      withJavaPlugin()
    }
  }

  fun createDaemonJvmPropertiesFile(sdk: Sdk) {
    val version = JavaVersion.tryParse(sdk.versionString!!)
    VfsTestUtil.createFile(myProjectRoot, "gradle/gradle-daemon-jvm.properties", "toolchainVersion=$version")
  }
}
