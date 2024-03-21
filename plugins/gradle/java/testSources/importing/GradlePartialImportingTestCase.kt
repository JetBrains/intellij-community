// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.testFramework.registerServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.jetbrains.plugins.gradle.model.Project as GradleProject
import org.jetbrains.plugins.gradle.service.project.*
import org.jetbrains.plugins.gradle.tooling.builder.ProjectPropertiesTestModelBuilder.ProjectProperties
import java.util.function.Predicate

abstract class GradlePartialImportingTestCase : BuildViewMessagesImportingTestCase() {

  override fun setUp() {
    super.setUp()
    myProject.registerServiceInstance(TestModelConsumer::class.java, TestModelConsumer())
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestPartialProjectResolverExtension(), testRootDisposable)
    ProjectModelContributor.EP_NAME.point.registerExtension(TestProjectModelContributor(myProject), testRootDisposable)
  }

  fun cleanupBeforeReImport() {
    TestModelConsumer.getInstance(myProject).clear()
  }

  fun assertReceivedModels(
    buildPath: String, projectName: String,
    expectedProjectLoadedModelsMap: Map<String, String>,
    expectedBuildFinishedModelsMap: Map<String, String>,
    receivedQuantity: Int = 1
  ) {
    val modelConsumer = TestModelConsumer.getInstance(myProject)

    val projectLoadedPredicate = Predicate<Pair<GradleProject, ProjectLoadedModel>> {
      val project = it.first
      project.name == projectName &&
      pathsEqual(project.projectIdentifier.buildIdentifier.rootDir.path, buildPath)
    }
    assertThat(modelConsumer.projectLoadedModels)
      .haveExactly(receivedQuantity, Condition(projectLoadedPredicate, "project loaded model for '$projectName' at '$buildPath'"))
    val (_, projectLoadedModel) = modelConsumer.projectLoadedModels.find(projectLoadedPredicate::test)!!
    assertThat(projectLoadedModel.map).containsExactlyInAnyOrderEntriesOf(expectedProjectLoadedModelsMap)

    val buildFinishedPredicate = Predicate<Pair<GradleProject, BuildFinishedModel>> {
      val project = it.first
      project.name == projectName &&
      pathsEqual(project.projectIdentifier.buildIdentifier.rootDir.path, buildPath)
    }
    assertThat(modelConsumer.buildFinishedModels)
      .haveExactly(receivedQuantity, Condition(buildFinishedPredicate, "build finished model for '$projectName' at '$buildPath'"))
    val (_, buildFinishedModel) = modelConsumer.buildFinishedModels.find(buildFinishedPredicate::test)!!
    assertThat(buildFinishedModel.map).containsExactlyInAnyOrderEntriesOf(expectedBuildFinishedModelsMap)
  }

  protected class TestPartialProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getToolingExtensionsClasses(): Set<Class<*>> {
      return setOf(ProjectProperties::class.java)
    }

    override fun getModelProviders() = listOf(
      ProjectLoadedModelProvider(),
      BuildFinishedModelProvider()
    )
  }

  private class TestProjectModelContributor(
    private val project: Project
  ) : ProjectModelContributor {

    override fun accept(
      modifiableGradleProjectModel: ModifiableGradleProjectModel,
      resolverContext: ProjectResolverContext
    ) {
      val modelConsumer = TestModelConsumer.getInstance(project)
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

  private class TestModelConsumer {

    val projectLoadedModels = ArrayList<Pair<GradleProject, ProjectLoadedModel>>()
    val buildFinishedModels = ArrayList<Pair<GradleProject, BuildFinishedModel>>()

    fun clear() {
      projectLoadedModels.clear()
      buildFinishedModels.clear()
    }

    companion object {

      fun getInstance(project: Project): TestModelConsumer {
        return project.service<TestModelConsumer>()
      }
    }
  }
}
