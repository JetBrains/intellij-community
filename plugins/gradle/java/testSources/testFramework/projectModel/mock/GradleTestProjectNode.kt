// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.util.io.toCanonicalPath
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

internal class GradleTestProjectNode {

  var projectPath: Path = Path.of("project")
  var numHolderModules: Int = 1
  var numSourceSetModules: Int = 1

  companion object {

    private fun createProjectNode(configuration: GradleTestProjectNode): DataNode<ProjectData> {
      val projectData = ProjectData(GradleConstants.SYSTEM_ID, "", "", configuration.projectPath.toCanonicalPath())
      val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
      repeat(configuration.numHolderModules) { holderModuleIndex ->
        val holderModuleName = "module-$holderModuleIndex"
        val holderModuleId = ":module-$holderModuleIndex"
        val holderModulePath = configuration.projectPath.resolve(holderModuleName).toCanonicalPath()
        val holderModuleData = ModuleData(holderModuleId, GradleConstants.SYSTEM_ID, "", holderModuleName, "", holderModulePath)
        val holderModuleNode = projectNode.createChild(ProjectKeys.MODULE, holderModuleData)
        repeat(configuration.numSourceSetModules) { sourceSetModuleIndex ->
          val sourceSetModuleName = "$holderModuleName.source-set-$sourceSetModuleIndex"
          val sourceSetModuleId = "$holderModuleId:source-set-$sourceSetModuleIndex"
          val sourceSetModuleData = GradleSourceSetData(sourceSetModuleId, sourceSetModuleId, sourceSetModuleName, "", holderModulePath)
          holderModuleNode.createChild(GradleSourceSetData.KEY, sourceSetModuleData)
        }
      }
      return projectNode
    }

    fun testProjectNode(configure: (GradleTestProjectNode) -> Unit): DataNode<ProjectData> {
      val configuration = GradleTestProjectNode()
      configure(configuration)
      return createProjectNode(configuration)
    }
  }
}
