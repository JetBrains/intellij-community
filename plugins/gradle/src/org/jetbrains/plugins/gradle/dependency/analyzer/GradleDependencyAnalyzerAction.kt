// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.dependencies.ArtifactDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencyNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.externalSystem.view.ProjectNode
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil

class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<ExternalSystemNode<*>>() {

  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): ExternalSystemNode<*>? {
    return e.getData(ExternalSystemDataKeys.SELECTED_NODES)?.firstOrNull()
  }

  override fun getModule(e: AnActionEvent, selectedData: ExternalSystemNode<*>): Module? {
    val project = e.project ?: return null
    return selectedData.findNode(ModuleNode::class.java)?.data
             ?.let { GradleUtil.findGradleModule(project, it) }
           ?: selectedData.findNode(ProjectNode::class.java)?.data
             ?.let { GradleUtil.findGradleModule(project, it) }
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

  override fun getDependencyScope(e: AnActionEvent, selectedData: ExternalSystemNode<*>): String? {
    return selectedData.findDependencyNode(DependencyScopeNode::class.java)?.scope
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
    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return null
    if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return module
    }
    return null
  }

  override fun getModule(e: AnActionEvent, selectedData: Module): Module {
    return selectedData
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: Module): DependencyAnalyzerDependency.Data {
    return DAModule(selectedData.name)
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: Module) = null

  override fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    return super.isEnabledAndVisible(e)
           && (e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) != null || !e.isFromContextMenu)
  }
}
