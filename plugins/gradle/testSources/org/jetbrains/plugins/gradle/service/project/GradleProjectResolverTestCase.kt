// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTree
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.ProjectModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder
import org.jetbrains.plugins.gradle.util.GradleConstants
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

  fun createModuleNode(moduleName: String): DataNode<ModuleData> {
    val moduleData = ModuleData("undefined", GradleConstants.SYSTEM_ID, "undefined", moduleName, "undefined", "undefined")
    return DataNode(ProjectKeys.MODULE, moduleData, null)
  }

  fun createSourceSetNode(sourceSetName: String): DataNode<GradleSourceSetData> {
    val sourceSetData = GradleSourceSetData("undefined", "undefined", "undefined:$sourceSetName", sourceSetName, "undefined", "undefined")
    return DataNode(GradleSourceSetData.KEY, sourceSetData, null)
  }

  fun createContentRoot(contentRootPath: Path): DataNode<ContentRootData> {
    val contentRoot = ContentRootData(GradleConstants.SYSTEM_ID, contentRootPath.toString())
    return DataNode(ProjectKeys.CONTENT_ROOT, contentRoot, null)
  }

  fun assertModuleNodeEquals(expectedModuleNode: DataNode<ModuleData>, actualModuleNode: DataNode<ModuleData>) {
    val expectedModuleTree = getModuleSimpleTree(expectedModuleNode)
    val actualModuleTree = getModuleSimpleTree(actualModuleNode)
    SimpleTreeAssertion.assertUnorderedTreeEquals(expectedModuleTree, actualModuleTree)
  }

  private fun getModuleSimpleTree(moduleNode: DataNode<ModuleData>): SimpleTree<Nothing?> {
    return buildTree {
      root(moduleNode.data.internalName, null) {
        for (sourceSetNode in ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY)) {
          node(sourceSetNode.data.internalName, null) {
            for (contentRootNode in ExternalSystemApiUtil.findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)) {
              node(contentRootNode.data.rootPath, null) {
                for (sourceRootType in ExternalSystemSourceType.entries) {
                  val sourceRoots = contentRootNode.getData().getPaths(sourceRootType)
                  if (sourceRoots.isNotEmpty()) {
                    node(sourceRootType.name, null) {
                      for (sourceRoot in sourceRoots) {
                        node(sourceRoot.path, null)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private class MockProjectResolverContext(
    private val models: GradleIdeaModelHolder,
  ) : ProjectResolverContext by notImplemented<ProjectResolverContext>() {
    override fun <T : Any?> getProjectModel(projectModel: ProjectModel, modelClass: Class<T?>): T? =
      models.getProjectModel(projectModel, modelClass)
  }

  private class MockExternalProject(
    private val projectPath: Path,
    private val buildPath: Path,
  ) : ExternalProject by notImplemented<ExternalProject>() {
    override fun getProjectDir(): File = projectPath.toFile()
    override fun getBuildDir(): File = buildPath.toFile()
  }

  private class MockProjectModel(private val projectIdentifier: ProjectIdentifier) : ProjectModel {
    override fun getProjectIdentifier(): ProjectIdentifier = projectIdentifier
  }
}