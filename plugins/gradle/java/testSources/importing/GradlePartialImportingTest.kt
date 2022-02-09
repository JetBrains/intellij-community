// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.testFramework.registerServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.ProjectModel
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.model.ModelsHolder
import org.jetbrains.plugins.gradle.model.Project
import org.jetbrains.plugins.gradle.model.ProjectImportAction
import org.jetbrains.plugins.gradle.service.project.*
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.builder.ProjectPropertiesTestModelBuilder.ProjectProperties
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.Test
import java.util.function.Consumer
import java.util.function.Predicate

class GradlePartialImportingTest : BuildViewMessagesImportingTestCase() {
  override fun setUp() {
    super.setUp()
    myProject.registerServiceInstance(ModelConsumer::class.java, ModelConsumer())
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestPartialProjectResolverExtension(), testRootDisposable)
    ProjectModelContributor.EP_NAME.point.registerExtension(TestProjectModelContributor(), testRootDisposable)
  }


  @Test
  fun `test re-import with partial project data resolve`() {
    createAndImportTestProject()
    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1"),
      mapOf("name" to "project", "prop_finished_2" to "val2")
    )

    val initialProjectStructure = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    createProjectSubFile(
      "gradle.properties",
      "prop_loaded_1=val1_inc\n" +
      "prop_finished_2=val2_inc\n"
    )

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(
      projectPath,
      ImportSpecBuilder(myProject, SYSTEM_ID)
        .use(ProgressExecutionMode.MODAL_SYNC)
        .projectResolverPolicy(
          GradlePartialResolverPolicy(Predicate { it is TestPartialProjectResolverExtension })
        )
    )

    assertSyncViewTreeEquals(
      "-\n" +
      " finished"
    )

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1_inc"),
      mapOf("name" to "project", "prop_finished_2" to "val2_inc")
    )

    val projectStructureAfterIncrementalImport = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    assertEquals(initialProjectStructure, projectStructureAfterIncrementalImport)
  }

  @Test
  @TargetVersions("3.3+")
  fun `test composite project partial re-import`() {
    createAndImportTestCompositeProject()

    // since Gradle 6.8, included (of the "main" build) builds became visible for `buildSrc` project
    // there are separate TAPI request per `buildSrc` project in a composite
    // and hence included build models can be handled more than once
    // should be fixed with https://github.com/gradle/gradle/issues/14563
    val includedBuildModelsReceivedQuantity = if (isGradleOlderThan("6.8")) 1 else 2

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1"),
      mapOf("name" to "project", "prop_finished_2" to "val2")
    )
    assertReceivedModels(
      path("buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )
    assertReceivedModels(
      path("includedBuild"), "includedBuild",
      mapOf("name" to "includedBuild", "prop_loaded_included" to "val1"),
      mapOf("name" to "includedBuild", "prop_finished_included" to "val2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild"), "subProject",
      mapOf("name" to "subProject", "prop_loaded_included" to "val1"),
      mapOf("name" to "subProject", "prop_finished_included" to "val2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild/buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )

    val initialProjectStructure = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    createProjectSubFile(
      "gradle.properties",
      "prop_loaded_1=val1_inc\n" +
      "prop_finished_2=val2_inc\n"
    )
    createProjectSubFile(
      "includedBuild/gradle.properties",
      "prop_loaded_included=val1_1\n" +
      "prop_finished_included=val2_2\n"
    )

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(
      projectPath,
      ImportSpecBuilder(myProject, SYSTEM_ID)
        .use(ProgressExecutionMode.MODAL_SYNC)
        .projectResolverPolicy(
          GradlePartialResolverPolicy(Predicate { it is TestPartialProjectResolverExtension })
        )
    )

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1_inc"),
      mapOf("name" to "project", "prop_finished_2" to "val2_inc")
    )
    assertReceivedModels(
      path("buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )
    assertReceivedModels(
      path("includedBuild"), "includedBuild",
      mapOf("name" to "includedBuild", "prop_loaded_included" to "val1_1"),
      mapOf("name" to "includedBuild", "prop_finished_included" to "val2_2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild"), "subProject",
      mapOf("name" to "subProject", "prop_loaded_included" to "val1_1"),
      mapOf("name" to "subProject", "prop_finished_included" to "val2_2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild/buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )

    val projectStructureAfterIncrementalImport = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    assertEquals(initialProjectStructure, projectStructureAfterIncrementalImport)
  }

  private fun createAndImportTestCompositeProject() {
    createProjectSubFile("buildSrc/build.gradle", buildscript {
      withGroovyPlugin()
      addImplementationDependency(code("gradleApi()"))
      addImplementationDependency(code("localGroovy()"))
    })
    createProjectSubFile(
      "gradle.properties",
      "prop_loaded_1=val1\n" +
      "prop_finished_2=val2\n"
    )
    createProjectSubFile("includedBuild/settings.gradle", "include 'subProject'")
    createProjectSubDir("includedBuild/subProject")
    createProjectSubFile("includedBuild/buildSrc/build.gradle", buildscript {
      withGroovyPlugin()
      addImplementationDependency(code("gradleApi()"))
      addImplementationDependency(code("localGroovy()"))
    })
    createSettingsFile("includeBuild 'includedBuild'")
    createProjectSubFile(
      "includedBuild/gradle.properties",
      "prop_loaded_included=val1\n" +
      "prop_finished_included=val2\n"
    )
    importProject("")
  }

  @Test
  fun `test import cancellation on project loaded phase`() {
    createAndImportTestProject()
    assertReceivedModels(projectPath, "project",
                         mapOf("name" to "project", "prop_loaded_1" to "val1"),
                         mapOf("name" to "project", "prop_finished_2" to "val2")
    )

    createProjectSubFile(
      "gradle.properties",
      "prop_loaded_1=error\n" +
      "prop_finished_2=val22\n"
    )

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(projectPath, ImportSpecBuilder(myProject, SYSTEM_ID).use(ProgressExecutionMode.MODAL_SYNC))

    if (currentGradleBaseVersion >= GradleVersion.version("4.8")) {
      if (currentGradleBaseVersion != GradleVersion.version("4.10.3")) {
        assertSyncViewTreeEquals { treeTestPresentation ->
          assertThat(treeTestPresentation).satisfiesAnyOf(
            Consumer {
              assertThat(it).isEqualTo("-\n" +
                                       " -failed\n" +
                                       "  Build cancelled")

            },
            Consumer {
              assertThat(it).isEqualTo("-\n" +
                                       " cancelled")

            },
            Consumer {
              assertThat(it).startsWith("-\n" +
                                        " -failed\n" +
                                        "  Build cancelled\n" +
                                        "  Could not build ")

            }
          )
        }
      }
      assertReceivedModels(projectPath, "project", mapOf("name" to "project", "prop_loaded_1" to "error"))
    }
    else {
      assertSyncViewTreeEquals(
        "-\n" +
        " finished"
      )
      assertReceivedModels(projectPath, "project",
                           mapOf("name" to "project", "prop_loaded_1" to "error"),
                           mapOf("name" to "project", "prop_finished_2" to "val22"))
    }
  }

  private fun cleanupBeforeReImport() {
    myProject.getService(ModelConsumer::class.java).projectLoadedModels.clear()
    myProject.getService(ModelConsumer::class.java).buildFinishedModels.clear()
  }

  private fun createAndImportTestProject() {
    createProjectSubFile(
      "gradle.properties",
      "prop_loaded_1=val1\n" +
      "prop_finished_2=val2\n"
    )

    importProject(buildscript { withJavaPlugin() })
  }

  private fun assertReceivedModels(
    buildPath: String, projectName: String,
    expectedProjectLoadedModelsMap: Map<String, String>,
    expectedBuildFinishedModelsMap: Map<String, String>? = null,
    receivedQuantity: Int = 1
  ) {
    val modelConsumer = myProject.getService(ModelConsumer::class.java)
    val projectLoadedPredicate = Predicate<Pair<Project, ProjectLoadedModel>> {
      val project = it.first
      project.name == projectName &&
      pathsEqual(project.projectIdentifier.buildIdentifier.rootDir.path, buildPath)
    }
    assertThat(modelConsumer.projectLoadedModels)
      .haveExactly(receivedQuantity, Condition(projectLoadedPredicate, "project loaded model for '$projectName' at '$buildPath'"))
    val (_, projectLoadedModel) = modelConsumer.projectLoadedModels.find(projectLoadedPredicate::test)!!
    assertThat(projectLoadedModel.map).containsExactlyInAnyOrderEntriesOf(expectedProjectLoadedModelsMap)
    if (expectedBuildFinishedModelsMap != null) {
      val buildFinishedPredicate = Predicate<Pair<Project, BuildFinishedModel>> {
        val project = it.first
        project.name == projectName &&
        pathsEqual(project.projectIdentifier.buildIdentifier.rootDir.path, buildPath)
      }
      assertThat(modelConsumer.buildFinishedModels)
        .haveExactly(receivedQuantity, Condition(buildFinishedPredicate, "build finished model for '$projectName' at '$buildPath'"))
      val (_, buildFinishedModel) = modelConsumer.buildFinishedModels.find(buildFinishedPredicate::test)!!
      assertThat(buildFinishedModel.map).containsExactlyInAnyOrderEntriesOf(expectedBuildFinishedModelsMap)
    }
    else {
      assertEmpty(modelConsumer.buildFinishedModels)
    }
  }
}

class TestPartialProjectResolverExtension : AbstractProjectResolverExtension() {

  override fun getToolingExtensionsClasses(): Set<Class<*>> {
    return setOf(ProjectProperties::class.java)
  }

  override fun projectsLoaded(models: ModelsHolder<BuildModel, ProjectModel>?) {
    val buildFinishedModel = models?.getModel(BuildFinishedModel::class.java)
    if (buildFinishedModel != null) {
      throw ProcessCanceledException(RuntimeException("buildFinishedModel should not be available for projectsLoaded callback"))
    }

    val projectLoadedModel = models?.getModel(ProjectLoadedModel::class.java)
    if (projectLoadedModel == null) {
      throw ProcessCanceledException(RuntimeException("projectLoadedModel should be available for projectsLoaded callback"))
    }

    if (projectLoadedModel.map.containsValue("error")) {
      val modelConsumer = resolverCtx.externalSystemTaskId.findProject()!!.getService(ModelConsumer::class.java)
      val build = (models as ProjectImportAction.AllModels).mainBuild
      for (project in build.projects) {
        modelConsumer.projectLoadedModels.add(project to models.getModel(project, ProjectLoadedModel::class.java)!!)
      }
      throw ProcessCanceledException(RuntimeException(projectLoadedModel.map.toString()))
    }
  }

  override fun getProjectsLoadedModelProvider() = ProjectLoadedModelProvider()

  override fun getModelProvider() = BuildFinishedModelProvider()
}

internal class TestProjectModelContributor : ProjectModelContributor {
  override fun accept(
    modifiableGradleProjectModel: ModifiableGradleProjectModel,
    toolingModelsProvider: ToolingModelsProvider,
    resolverContext: ProjectResolverContext
  ) {
    val modelConsumer = resolverContext.externalSystemTaskId.findProject()!!.getService(ModelConsumer::class.java)
    toolingModelsProvider.projects().forEach {
      modelConsumer.projectLoadedModels.add(it to toolingModelsProvider.getProjectModel(it, ProjectLoadedModel::class.java)!!)
      modelConsumer.buildFinishedModels.add(it to toolingModelsProvider.getProjectModel(it, BuildFinishedModel::class.java)!!)
    }
  }
}

internal data class ModelConsumer(
  val projectLoadedModels: MutableList<Pair<Project, ProjectLoadedModel>> = mutableListOf(),
  val buildFinishedModels: MutableList<Pair<Project, BuildFinishedModel>> = mutableListOf()
)
