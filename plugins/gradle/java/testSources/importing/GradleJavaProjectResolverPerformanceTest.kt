// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.JavaGradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestExternalProject.Companion.externalProjects
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestIdeaProject.Companion.ideaProject
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectNode.Companion.projectNode
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectResolverContext.Companion.projectResolverContext
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleNodes
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleSdkNode
import org.jetbrains.plugins.gradle.testFramework.projectModel.projectSdkNode
import org.jetbrains.plugins.gradle.util.gradleSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
@PerformanceUnitTest
@ParameterizedClass
@BaseGradleVersionSource
class GradleJavaProjectResolverPerformanceTest(gradleVersion: GradleVersion) {

  private val testRootFixture = tempPathFixture()

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val project by gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)

  @Nested
  @PerformanceUnitTest
  inner class SdkData {

    @ParameterizedTest
    @CsvSource("10000, 10", "1000, 100")
    fun `test JavaGradleProjectResolver#populateProjectExtraModels`(numHolderModules: Int, numSourceSetModules: Int) {
      `test JavaGradleProjectResolver`(numHolderModules, numSourceSetModules) { projectResolver, ideaProject, projectNode ->
        projectResolver.populateJavaProjectCompilerSettings(ideaProject, projectNode)
        Assertions.assertEquals(gradle.gradleJvm, projectNode.projectSdkNode?.data?.sdkName) {
          "Incorrect ProjectSdkData is populated for ${ideaProject.name}"
        }
      }
    }

    @ParameterizedTest
    @CsvSource("10000, 10", "1000, 100")
    fun `test JavaGradleProjectResolver#populateModuleExtraModels`(numHolderModules: Int, numSourceSetModules: Int) {
      `test JavaGradleProjectResolver`(numHolderModules, numSourceSetModules) { projectResolver, ideaProject, projectNode ->
        for ((ideaModule, moduleNode) in ideaProject.modules.zip(projectNode.moduleNodes)) {
          projectResolver.populateJavaModuleCompilerSettings(ideaModule, moduleNode)
          Assertions.assertEquals(gradle.gradleJvm, moduleNode.moduleSdkNode?.data?.sdkName) {
            "Incorrect ModuleSdkData is populated for ${ideaModule.name}"
          }
        }
      }
    }

    private fun `test JavaGradleProjectResolver`(
      numHolderModules: Int, numSourceSetModules: Int,
      test: (JavaGradleProjectResolver, IdeaProject, DataNode<ProjectData>) -> Unit,
    ) {
      setGradleJvm(project, gradle.gradleJvm)

      val ideaProject = ideaProject(project, gradle.gradleJvm) {
        it.numHolderModules = numHolderModules
      }
      val externalProjects = externalProjects {
        it.numHolderModules = numHolderModules
        it.numSourceSetModules = numSourceSetModules
      }
      val projectNode = projectNode(project) {
        it.numHolderModules = numHolderModules
        it.numSourceSetModules = numSourceSetModules
      }
      val projectResolverContext = projectResolverContext(project, ideaProject, externalProjects)
      val projectResolver = javaProjectResolver(projectResolverContext)

      val setup = {
        // Removes the project resolver's caches
        projectResolver.resolveFinished(projectNode)
        // Removes the resolved SDK data from the data nodes' graph
        projectNode.projectSdkNode?.clear(/* removeFromGraph = */ true)
        for (moduleNode in projectNode.moduleNodes) {
          moduleNode.moduleSdkNode?.clear(/* removeFromGraph = */ true)
        }
      }

      val test = {
        test(projectResolver, ideaProject, projectNode)
      }

      Benchmark.newBenchmark("$numHolderModules x $numSourceSetModules modules", test)
        .setup(setup)
        .runAsStressTest()
        .start()
    }
  }

  @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
  private class GradleTestNoOpProjectResolverExtension :
    GradleProjectResolverExtension by notImplemented<GradleProjectResolverExtension>() {
    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
    override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {}
  }

  companion object {

    fun javaProjectResolver(context: ProjectResolverContext): JavaGradleProjectResolver = JavaGradleProjectResolver().apply {
      setProjectResolverContext(context)
      setNext(GradleTestNoOpProjectResolverExtension())
    }

    fun setGradleJvm(project: Project, gradleJvm: String) {
      project.gradleSettings.linkedProjectsSettings = listOf(
        GradleProjectSettings(project.basePath!!).also { it.gradleJvm = gradleJvm }
      )
    }
  }
}
