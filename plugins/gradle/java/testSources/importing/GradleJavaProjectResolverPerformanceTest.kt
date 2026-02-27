// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.gradle.tooling.model.ProjectModel
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.JavaGradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestExternalProject.Companion.testExternalProjects
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestIdeaProject.Companion.testIdeaProject
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectNode.Companion.testProjectNode
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleNodes
import org.jetbrains.plugins.gradle.testFramework.projectModel.moduleSdkNode
import org.jetbrains.plugins.gradle.testFramework.projectModel.projectSdkNode
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
@PerformanceUnitTest
class GradleJavaProjectResolverPerformanceTest {

  @Nested
  inner class SdkData {

    private val gradleJvmFixture = gradleJvmFixture(GradleVersion.current(), JavaVersionRestriction.NO)
    private val gradleJvm get() = gradleJvmFixture.get().gradleJvm

    //private val project by projectFixture()
    // BUG! IJPL-239938 GlobalWorkspaceModel: externally-added entities (e.g. SDKs) get wiped when a new project opens
    private val project by testFixture {
      gradleJvmFixture.init()
      initialized(projectFixture().init()) {}
    }

    @ParameterizedTest
    @CsvSource("10000, 10", "1000, 100")
    fun `test JavaGradleProjectResolver#populateProjectExtraModels`(numHolderModules: Int, numSourceSetModules: Int) {
      `test JavaGradleProjectResolver`(numHolderModules, numSourceSetModules) { projectResolver, ideaProject, projectNode ->
        projectResolver.populateJavaProjectCompilerSettings(ideaProject, projectNode)
        Assertions.assertEquals(gradleJvm, projectNode.projectSdkNode?.data?.sdkName) {
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
          Assertions.assertEquals(gradleJvm, moduleNode.moduleSdkNode?.data?.sdkName) {
            "Incorrect ModuleSdkData is populated for ${ideaModule.name}"
          }
        }
      }
    }

    private fun `test JavaGradleProjectResolver`(
      numHolderModules: Int, numSourceSetModules: Int,
      test: (JavaGradleProjectResolver, IdeaProject, DataNode<ProjectData>) -> Unit,
    ) {
      val ideaProject = testIdeaProject {
        it.numHolderModules = numHolderModules
        it.projectName = project.name
        it.projectSdkName = gradleJvm
        it.moduleSdkName = gradleJvm
      }
      val externalProjects = testExternalProjects {
        it.numHolderModules = numHolderModules
        it.numSourceSetModules = numSourceSetModules
      }
      val projectNode = testProjectNode {
        it.projectPath = project.basePath?.toNioPathOrNull()!!
        it.numHolderModules = numHolderModules
        it.numSourceSetModules = numSourceSetModules
      }
      val syncContext = TestProjectResolverContext(project, ideaProject, externalProjects)
      val projectResolver = JavaGradleProjectResolver().also {
        it.setProjectResolverContext(syncContext)
        it.setNext(NoOpNextResolver())
      }

      val setup = {
        project.gradleSettings.linkedProjectsSettings = listOf(
          GradleProjectSettings(project.basePath!!).also {
            it.gradleJvm = gradleJvm
          }
        )
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
  private class TestProjectResolverContext(
    override val project: Project,
    ideaProject: IdeaProject,
    externalProjects: List<ExternalProject>,
  ) : ProjectResolverContext by notImplemented<ProjectResolverContext>() {

    private val externalProjectModels = ideaProject.modules.zip(externalProjects).toMap()

    override val projectPath: String = project.basePath!!
    override val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project)
    override fun getBuildSrcGroup(): String? = null
    override fun isResolveModulePerSourceSet(): Boolean = true
    override fun getExternalSystemTaskId(): ExternalSystemTaskId = taskId

    @Suppress("UNCHECKED_CAST")
    override fun <T> getProjectModel(projectModel: ProjectModel, modelClass: Class<T>): T? =
      when (modelClass) {
        ExternalProject::class.java -> externalProjectModels[projectModel] as T
        GradleSourceSetModel::class.java -> externalProjectModels[projectModel]?.sourceSetModel as T
        else -> throw AssertionError("Unexpected model class requested in performance test: $modelClass")
      }

    private val userDataHolder = UserDataHolderBase()
    override fun <T> getUserData(key: Key<T>): T? = userDataHolder.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = userDataHolder.putUserData(key, value)
    override fun <T : Any> putUserDataIfAbsent(key: Key<T>, value: T): T = userDataHolder.putUserDataIfAbsent(key, value)
    override fun <T> replace(key: Key<T>, oldValue: T?, newValue: T?): Boolean = userDataHolder.replace(key, oldValue, newValue)
  }

  @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
  private class NoOpNextResolver : GradleProjectResolverExtension by notImplemented<GradleProjectResolverExtension>() {
    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
    override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {}
  }
}
