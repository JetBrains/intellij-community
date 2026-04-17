// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.util.environment.TestEnvironment.Companion.useEnvironmentVariables
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleJavaProjectResolverPerformanceTest.Companion.javaProjectResolver
import org.jetbrains.plugins.gradle.importing.GradleJavaProjectResolverPerformanceTest.Companion.setGradleJvm
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestExternalProject.Companion.externalProjects
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestIdeaProject.Companion.ideaProject
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectNode.Companion.projectNode
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectResolverContext.Companion.projectResolverContext
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleNodes
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleSdkNode
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@TestApplication
class GradleJavaProjectResolverTest {

  @Nested
  inner class SdkData {

    private val project by projectFixture()

    private val mockSdk by testFixture {
      val sdkHomePath = GradleJvmResolver.resolveGradleJvmHomePath(GradleVersion.current(), JavaVersionRestriction.NO)
      val sdkName = JavaSdk.getInstance().suggestSdkName(null, sdkHomePath)
      val sdk = IdeaTestUtil.createMockJdk(sdkName, sdkHomePath)
      initialized(sdk) {}
    }

    @Test
    fun `test lookupGradleJvmIfMatches reuses registered Gradle JVM when version matches`(): Unit = runBlocking {
      registerSdkInSdkTable(mockSdk)

      setGradleJvm(project, mockSdk.name)

      val ideaProject = ideaProject(project, mockSdk.name)
      val externalProjects = externalProjects()
      val projectNode = projectNode(project)

      val projectResolverContext = projectResolverContext(project, ideaProject, externalProjects)
      val projectResolver = javaProjectResolver(projectResolverContext)

      val ideaModule = ideaProject.modules.single()
      val moduleNode = projectNode.moduleNodes.single()
      projectResolver.populateJavaModuleCompilerSettings(ideaModule, moduleNode)

      assertEquals(mockSdk.name, moduleNode.moduleSdkNode?.data?.sdkName)
    }

    @Test
    fun `test lookupGradleJvmIfMatches returns null when Gradle JVM is invalid`() {
      setGradleJvm(project, "non-existent-sdk")

      val ideaProject = ideaProject(project, mockSdk.name)
      val externalProjects = externalProjects()
      val projectNode = projectNode(project)

      val projectResolverContext = projectResolverContext(project, ideaProject, externalProjects)
      val projectResolver = javaProjectResolver(projectResolverContext)

      val ideaModule = ideaProject.modules.single()
      val moduleNode = projectNode.moduleNodes.single()
      projectResolver.populateJavaModuleCompilerSettings(ideaModule, moduleNode)

      assertNull(moduleNode.moduleSdkNode?.data?.sdkName)
    }

    @Test
    fun `test lookupGradleJvmIfMatches skips Gradle JVM that is not registered in SDK table`() {
      assertFalse(ExternalSystemJdkUtil.isSdkRegisteredInSdkTable(project, mockSdk))

      setGradleJvm(project, ExternalSystemJdkUtil.USE_JAVA_HOME)

      val ideaProject = ideaProject(project, mockSdk.name)
      val externalProjects = externalProjects()
      val projectNode = projectNode(project)

      val projectResolverContext = projectResolverContext(project, ideaProject, externalProjects)
      val projectResolver = javaProjectResolver(projectResolverContext)

      useEnvironmentVariables(JAVA_HOME to mockSdk.homePath) {
        val ideaModule = ideaProject.modules.single()
        val moduleNode = projectNode.moduleNodes.single()
        projectResolver.populateJavaModuleCompilerSettings(ideaModule, moduleNode)

        assertNull(moduleNode.moduleSdkNode?.data?.sdkName)
      }
    }
  }

  companion object {
    private suspend fun CoroutineScope.registerSdkInSdkTable(sdk: Sdk) {
      val jdkTable = ProjectJdkTable.getInstance()
      writeAction {
        jdkTable.addJdk(sdk, asDisposable())
      }
    }
  }
}
