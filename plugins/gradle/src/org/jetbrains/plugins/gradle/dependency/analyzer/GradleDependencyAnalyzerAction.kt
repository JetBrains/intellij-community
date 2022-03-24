// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.project.dependencies.ArtifactDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencyNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.externalSystem.view.ProjectNode
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.module.Module

class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<ExternalSystemNode<*>>() {

  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): ExternalSystemNode<*>? {
    return e.getData(ExternalSystemDataKeys.SELECTED_NODES)?.firstOrNull()
  }

  override fun getExternalProjectPath(e: AnActionEvent, selectedData: ExternalSystemNode<*>): String? {
    return selectedData.findNode(ModuleNode::class.java)
             ?.data?.linkedExternalProjectPath
           ?: selectedData.findNode(ProjectNode::class.java)
             ?.data?.linkedExternalProjectPath
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: ExternalSystemNode<*>): DependencyAnalyzerDependency.Data? {
    return when (val data = selectedData.data) {
      is ProjectData -> DAModule(data.internalName)
      is ModuleData -> DAModule(data.moduleName)
      else -> when (val node = selectedData.dependencyNode) {
        is ProjectDependencyNode -> DAModule(node.projectName)
        is ArtifactDependencyNode -> DAArtifact(node.group, node.module, node.version)
        else -> null
      }
    }
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: ExternalSystemNode<*>): DependencyAnalyzerDependency.Scope? {
    val dependencyNode = selectedData.findDependencyNode(DependencyScopeNode::class.java) ?: return null
    return DAScope(dependencyNode.scope, StringUtil.toTitleCase(dependencyNode.scope))
  }
}

class ToolbarDependencyAnalyzerAction : DependencyAnalyzerAction() {

  private val viewAction = ViewDependencyAnalyzerAction()

  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun isEnabledAndVisible(e: AnActionEvent) = true

  override fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent) {
    viewAction.setSelectedState(view, e)
  }
}

class ProjectViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<Module>() {

  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): Module? {
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) ?: return null
    return modules.find { ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it) }
  }

  override fun getExternalProjectPath(e: AnActionEvent, selectedData: Module): String? {
    return ExternalSystemApiUtil.getExternalProjectPath(selectedData)
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: Module): DependencyAnalyzerDependency.Data {
    return DAModule(selectedData.name)
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: Module) = null
}
