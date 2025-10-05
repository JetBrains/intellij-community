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
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.ProjectModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Path

abstract class GradleProjectResolverTestCase {

  fun createProjectModel(buildPath: Path, projectId: String): ProjectModel {
    return mock<ProjectModel> {
      on { projectIdentifier } doReturn DefaultProjectIdentifier(buildPath.toFile(), projectId)
    }
  }

  fun createExternalProject(projectPath: Path): ExternalProject {
    return createExternalProject(projectPath, projectPath.resolve("build"))
  }

  fun createExternalProject(projectPath: Path, projectBuildPath: Path): ExternalProject {
    return mock<ExternalProject> {
      on { projectDir } doReturn projectPath.toFile()
      on { buildDir } doReturn projectBuildPath.toFile()
    }
  }

  fun createResolveContext(vararg projectModels: Pair<ProjectModel, ExternalProject>): ProjectResolverContext {
    return mock<ProjectResolverContext> {
      for ((projectModel, externalProject) in projectModels) {
        on { getProjectModel(projectModel, ExternalProject::class.java) } doReturn externalProject
      }
    }
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
}