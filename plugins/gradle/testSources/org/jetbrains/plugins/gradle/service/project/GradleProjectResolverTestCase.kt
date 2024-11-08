// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import com.intellij.platform.externalSystem.testFramework.utils.module.ExternalSystemSourceRootAssertion
import com.intellij.testFramework.utils.module.assertEqualsUnordered
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.ProjectModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.file.Path

abstract class GradleProjectResolverTestCase {

  fun createProjectModel(buildPath: Path, projectId: String): ProjectModel {
    return MockProjectModel(
      DefaultProjectIdentifier(
        buildPath.toFile(),
        projectId
      )
    )
  }

  fun createExternalProject(projectPath: Path): ExternalProject {
    return createExternalProject(projectPath, projectPath.resolve("build"))
  }

  fun createExternalProject(projectPath: Path, projectBuildPath: Path): ExternalProject {
    return MockExternalProject(projectPath, projectBuildPath)
  }

  fun createResolveContext(vararg projectModels: Pair<ProjectModel, ExternalProject>): ProjectResolverContext {
    val models = GradleIdeaModelHolder()
    for ((projectModel, externalProject) in projectModels) {
      models.addProjectModel(projectModel, ExternalProject::class.java, externalProject)
    }
    return MockProjectResolverContext(models)
  }

  fun createModuleNode(): DataNode<ModuleData> {
    val moduleData = ModuleData("undefined", GradleConstants.SYSTEM_ID, "undefined", "undefined", "undefined", "undefined")
    return DataNode(ProjectKeys.MODULE, moduleData, null)
  }

  fun createSourceSetNode(sourceSetName: String): DataNode<GradleSourceSetData> {
    val sourceSetData = GradleSourceSetData("undefined", "undefined", "undefined:$sourceSetName", "undefined", "undefined", "undefined")
    return DataNode(GradleSourceSetData.KEY, sourceSetData, null)
  }

  fun createContentRoot(contentRootPath: Path): DataNode<ContentRootData> {
    val contentRoot = ContentRootData(GradleConstants.SYSTEM_ID, contentRootPath.toString())
    return DataNode(ProjectKeys.CONTENT_ROOT, contentRoot, null)
  }

  fun assertContentRoots(moduleNode: DataNode<ModuleData>, vararg expectedContentRoots: Path) {
    val actualContentRoots = getContentRoots(moduleNode)
    assertEqualsUnordered(expectedContentRoots.toSet(), actualContentRoots.keys)
  }

  fun assertSourceRoots(
    moduleNode: DataNode<ModuleData>,
    contentRootPath: Path,
    configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit,
  ) {
    val contentRoots = getContentRoots(moduleNode)
    val contentRoot = contentRoots[contentRootPath]
    Assertions.assertNotNull(contentRoot) {
      "The content root '$contentRootPath' doesn't exist."
    }
    assertSourceRoots(contentRoot!!, configure)
  }

  private fun assertSourceRoots(contentRoot: ContentRootData, configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit) {
    ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedSourcePaths ->
      val sourcePaths = contentRoot.getPaths(type).map { Path.of(it.path) }
      assertEqualsUnordered(expectedSourcePaths, sourcePaths) {
        "Source root of type $type in $contentRoot"
      }
    }
  }

  private fun getContentRoots(moduleNode: DataNode<ModuleData>): Map<Path, ContentRootData> {
    return ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY)
      .flatMap { ExternalSystemApiUtil.findAll(it, ProjectKeys.CONTENT_ROOT) }
      .associate { Path.of(it.data.rootPath) to it.data }
  }

  private class MockProjectResolverContext(private val models: GradleIdeaModelHolder) : ProjectResolverContext {

    override fun <T : Any?> getProjectModel(projectModel: ProjectModel, modelClass: Class<T?>): T? =
      models.getProjectModel(projectModel, modelClass)

    override fun getExternalSystemTaskId() = throw UnsupportedOperationException()
    override fun getIdeProjectPath() = throw UnsupportedOperationException()
    override fun getProjectPath() = throw UnsupportedOperationException()
    override fun getSettings() = throw UnsupportedOperationException()
    override fun getConnection() = throw UnsupportedOperationException()
    override fun getCancellationToken() = throw UnsupportedOperationException()
    override fun getListener() = throw UnsupportedOperationException()
    override fun isPhasedSyncEnabled() = throw UnsupportedOperationException()
    override fun isResolveModulePerSourceSet() = throw UnsupportedOperationException()
    override fun isUseQualifiedModuleNames() = throw UnsupportedOperationException()
    override fun getBuildEnvironment() = throw UnsupportedOperationException()
    override fun getRootBuild() = throw UnsupportedOperationException()
    override fun getNestedBuilds() = throw UnsupportedOperationException()
    override fun getAllBuilds() = throw UnsupportedOperationException()
    override fun <T : Any?> getRootModel(modelClass: Class<T?>) = throw UnsupportedOperationException()
    override fun <T : Any?> getBuildModel(buildModel: BuildModel, modelClass: Class<T?>) = throw UnsupportedOperationException()
    override fun hasModulesWithModel(modelClass: Class<*>) = throw UnsupportedOperationException()
    override fun getProjectGradleVersion() = throw UnsupportedOperationException()
    override fun getBuildSrcGroup() = throw UnsupportedOperationException()
    override fun getBuildSrcGroup(rootName: String, buildIdentifier: BuildIdentifier) = throw UnsupportedOperationException()
    override fun report(kind: MessageEvent.Kind, buildIssue: BuildIssue) = throw UnsupportedOperationException()
    override fun getPolicy() = throw UnsupportedOperationException()
    override fun getArtifactsMap() = throw UnsupportedOperationException()
    override fun <T : Any?> putUserDataIfAbsent(key: Key<T?>, value: T & Any) = throw UnsupportedOperationException()
    override fun <T : Any?> replace(key: Key<T?>, oldValue: T?, newValue: T?) = throw UnsupportedOperationException()
    override fun <T : Any?> getUserData(key: Key<T?>) = throw UnsupportedOperationException()
    override fun <T : Any?> putUserData(key: Key<T?>, value: T?) = throw UnsupportedOperationException()
  }

  private class MockExternalProject(
    private val projectPath: Path,
    private val buildPath: Path,
  ) : ExternalProject {
    override fun getProjectDir(): File = projectPath.toFile()
    override fun getBuildDir(): File = buildPath.toFile()

    override fun getExternalSystemId() = throw UnsupportedOperationException()
    override fun getId() = throw UnsupportedOperationException()
    override fun getPath() = throw UnsupportedOperationException()
    override fun getIdentityPath() = throw UnsupportedOperationException()
    override fun getName() = throw UnsupportedOperationException()
    override fun getQName() = throw UnsupportedOperationException()
    override fun getDescription() = throw UnsupportedOperationException()
    override fun getGroup() = throw UnsupportedOperationException()
    override fun getVersion() = throw UnsupportedOperationException()
    override fun getSourceCompatibility() = throw UnsupportedOperationException()
    override fun getTargetCompatibility() = throw UnsupportedOperationException()
    override fun getChildProjects() = throw UnsupportedOperationException()
    override fun getBuildFile() = throw UnsupportedOperationException()
    override fun getTasks() = throw UnsupportedOperationException()
    override fun getSourceSets() = throw UnsupportedOperationException()
    override fun getArtifacts() = throw UnsupportedOperationException()
    override fun getArtifactsByConfiguration() = throw UnsupportedOperationException()
    override fun getSourceSetModel() = throw UnsupportedOperationException()
    override fun getTaskModel() = throw UnsupportedOperationException()
  }

  private class MockProjectModel(private val projectIdentifier: ProjectIdentifier) : ProjectModel {
    override fun getProjectIdentifier(): ProjectIdentifier = projectIdentifier
  }
}