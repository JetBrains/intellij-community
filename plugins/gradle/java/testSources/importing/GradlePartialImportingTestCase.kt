// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.testFramework.registerServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.ProjectModel
import org.jetbrains.plugins.gradle.model.ModelsHolder
import org.jetbrains.plugins.gradle.model.Project
import org.jetbrains.plugins.gradle.model.ProjectImportAction
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

    override fun projectsLoaded(models: ModelsHolder<BuildModel, ProjectModel>?) {
      val buildFinishedModel = models?.getModel(BuildFinishedModel::class.java)
      if (buildFinishedModel != null) {
        throw ProcessCanceledException(RuntimeException("buildFinishedModel should not be available for projectsLoaded callback"))
      }

      val rootProjectLoadedModel = models?.getModel(ProjectLoadedModel::class.java)
      if (rootProjectLoadedModel == null) {
        throw ProcessCanceledException(RuntimeException("projectLoadedModel should be available for projectsLoaded callback"))
      }

      if (rootProjectLoadedModel.map.containsValue("error")) {
        val project = resolverCtx.externalSystemTaskId.findProject()!!
        val modelConsumer = project.getService(ModelConsumer::class.java)
        val build = (models as ProjectImportAction.AllModels).mainBuild
        for (gradleProject in build.projects) {
          val projectLoadedModel = models.getModel(gradleProject, ProjectLoadedModel::class.java)!!
          modelConsumer.projectLoadedModels.add(gradleProject to projectLoadedModel)
        }
        throw ProcessCanceledException(RuntimeException(rootProjectLoadedModel.map.toString()))
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
      toolingModelsProvider: ToolingModelsProvider,
      resolverContext: ProjectResolverContext
    ) {
      val project = resolverContext.externalSystemTaskId.findProject()!!
      val modelConsumer = project.getService(ModelConsumer::class.java)
      toolingModelsProvider.projects().forEach {
        modelConsumer.projectLoadedModels.add(it to toolingModelsProvider.getProjectModel(it, ProjectLoadedModel::class.java)!!)
        modelConsumer.buildFinishedModels.add(it to toolingModelsProvider.getProjectModel(it, BuildFinishedModel::class.java)!!)
      }
    }
  }

  private data class ModelConsumer(
    val projectLoadedModels: MutableList<Pair<Project, ProjectLoadedModel>> = mutableListOf(),
    val buildFinishedModels: MutableList<Pair<Project, BuildFinishedModel>> = mutableListOf()
  )
}
