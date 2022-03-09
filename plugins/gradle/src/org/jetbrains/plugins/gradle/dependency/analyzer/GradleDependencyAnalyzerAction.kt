// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.project.dependencies.ArtifactDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencyNode
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.externalSystem.view.ProjectNode
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun setSelectedState(e: AnActionEvent, view: DependencyAnalyzerView) {
    val selectedNode = getSelectedNode(e.dataContext) ?: return
    val externalProjectPath = getExternalProjectPath(selectedNode) ?: return
    val dependencyData = getDependencyData(selectedNode)
    val dependencyScope = getDependencyScope(selectedNode)
    if (dependencyData != null && dependencyScope != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData, dependencyScope)
    }
    else if (dependencyData != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData)
    }
    else {
      view.setSelectedExternalProject(externalProjectPath)
    }
  }

  private fun getSelectedNode(context: DataContext): ExternalSystemNode<*>? {
    return ExternalSystemDataKeys.SELECTED_NODES.getData(context)?.firstOrNull()
  }

  private fun getExternalProjectPath(selectedNode: ExternalSystemNode<*>): String? {
    if (selectedNode is ProjectNode) {
      return selectedNode.data?.linkedExternalProjectPath
    }
    return selectedNode.findNode(ModuleNode::class.java)
      ?.data?.linkedExternalProjectPath
  }

  private fun getDependencyData(selectedNode: ExternalSystemNode<*>): DependencyAnalyzerDependency.Data? {
    return when (val data = selectedNode.data) {
      is ProjectData -> DAModule(data.internalName)
      is ModuleData -> DAModule(data.internalName)
      else -> when (val node = selectedNode.dependencyNode) {
        is ProjectDependencyNode -> DAModule(node.projectName)
        is ArtifactDependencyNode -> DAArtifact(node.group, node.module, node.version)
        else -> null
      }
    }
  }

  private fun getDependencyScope(selectedNode: ExternalSystemNode<*>): DependencyAnalyzerDependency.Scope? {
    val dependencyNode = selectedNode.findDependencyNode(DependencyScopeNode::class.java) ?: return null
    return DAScope(dependencyNode.scope, StringUtil.toTitleCase(dependencyNode.scope))
  }
}