// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.testFramework.registerServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.jetbrains.plugins.gradle.model.Project
import org.jetbrains.plugins.gradle.service.buildActionRunner.GradleBuildActionListener
import org.jetbrains.plugins.gradle.service.project.*
import org.jetbrains.plugins.gradle.tooling.builder.ProjectPropertiesTestModelBuilder.ProjectProperties
import java.util.function.Predicate

abstract class GradlePartialImportingTestCase : BuildViewMessagesImportingTestCase() {

  override fun setUp() {
    super.setUp()
    myProject.registerServiceInstance(ModelConsumer::class.java, ModelConsumer())
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestPartialProjectResolverExtension(), testRootDisposable)
    ProjectModelContributor.EP_NAME.point.registerExtension(TestProjectModelContributor(), testRootDisposable)
  }

  fun cleanupBeforeReImport() {
    myProject.getService(ModelConsumer::class.java).projectLoadedModels.clear()
    myProject.getService(ModelConsumer::class.java).buildFinishedModels.clear()
  }

  fun assertReceivedModels(
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

  protected class TestPartialProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getToolingExtensionsClasses(): Set<Class<*>> {
      return setOf(ProjectProperties::class.java)
    }

    override fun createBuildListener() = object : GradleBuildActionListener {
      override fun onProjectLoaded() {
        val rootProjectLoadedModel = resolverCtx.getRootModel(ProjectLoadedModel::class.java)
        if (rootProjectLoadedModel == null) {
          throw ProcessCanceledException(RuntimeException("projectLoadedModel should be available for onProjectLoaded callback"))
        }

        if (rootProjectLoadedModel.map.containsValue("error")) {
          val project = resolverCtx.externalSystemTaskId.findProject()!!
          val modelConsumer = project.getService(ModelConsumer::class.java)
          for (gradleProject in resolverCtx.rootBuild.projects) {
            val projectLoadedModel = resolverCtx.getProjectModel(gradleProject, ProjectLoadedModel::class.java)!!
            modelConsumer.projectLoadedModels.add(gradleProject to projectLoadedModel)
          }
          throw ProcessCanceledException(RuntimeException(rootProjectLoadedModel.map.toString()))
        }
      }

      override fun onBuildCompleted() {
        val rootBuildFinishedModel = resolverCtx.getRootModel(BuildFinishedModel::class.java)
        if (rootBuildFinishedModel == null) {
          throw ProcessCanceledException(RuntimeException("buildFinishedModel should be available for onBuildCompleted callback"))
        }
        val rootProjectLoadedModel = resolverCtx.getRootModel(ProjectLoadedModel::class.java)
        if (rootProjectLoadedModel == null) {
          throw ProcessCanceledException(RuntimeException("projectLoadedModel should be available for onBuildCompleted callback"))
        }

        if (rootBuildFinishedModel.map.containsValue("error")) {
          val project = resolverCtx.externalSystemTaskId.findProject()!!
          val modelConsumer = project.getService(ModelConsumer::class.java)
          for (gradleProject in resolverCtx.rootBuild.projects) {
            val buildFinishedModel = resolverCtx.getProjectModel(gradleProject, BuildFinishedModel::class.java)!!
            modelConsumer.buildFinishedModels.add(gradleProject to buildFinishedModel)
          }
          throw ProcessCanceledException(RuntimeException(rootBuildFinishedModel.map.toString()))
        }
      }
    }

    override fun getModelProviders() = listOf(
      ProjectLoadedModelProvider(),
      BuildFinishedModelProvider()
    )
  }

  private class TestProjectModelContributor : ProjectModelContributor {
    override fun accept(
      modifiableGradleProjectModel: ModifiableGradleProjectModel,
      resolverContext: ProjectResolverContext
    ) {
      val project = resolverContext.externalSystemTaskId.findProject()!!
      val modelConsumer = project.getService(ModelConsumer::class.java)
      for (buildModel in resolverContext.allBuilds) {
        for (projectModel in buildModel.projects) {
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, ProjectLoadedModel::class.java)!!
          val buildFinishedModel = resolverContext.getProjectModel(projectModel, BuildFinishedModel::class.java)!!
          modelConsumer.projectLoadedModels.add(projectModel to projectLoadedModel)
          modelConsumer.buildFinishedModels.add(projectModel to buildFinishedModel)
        }
      }
    }
  }

  private data class ModelConsumer(
    val projectLoadedModels: MutableList<Pair<Project, ProjectLoadedModel>> = mutableListOf(),
    val buildFinishedModels: MutableList<Pair<Project, BuildFinishedModel>> = mutableListOf()
  )
}
