// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.module.Module

class GradleDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent) = GradleConstants.SYSTEM_ID

  override fun setSelectedState(e: AnActionEvent, view: DependencyAnalyzerView) {
    val externalProjectPath = getExternalProjectPath(e.dataContext) ?: return
    val dependencyData = getSelectedDependencyData(e.dataContext) ?: return
    view.setSelectedDependency(externalProjectPath, dependencyData)
  }

  private fun getExternalProjectPath(context: DataContext): String? {
    val module = getGradleModule(context) ?: findGradleModule(context)
    return ExternalSystemApiUtil.getExternalProjectPath(module)
  }

  private fun getGradleModule(context: DataContext): Module? {
    val module = ExternalSystemActionUtil.getModule(context) ?: return null
    return if (module.isGradleModule()) module else null
  }

  private fun findGradleModule(context: DataContext): Module? {
    val project = CommonDataKeys.PROJECT.getData(context) ?: return null
    val selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(context)
    val moduleName = when (val data = selectedNodes?.firstOrNull()?.data) {
      is ProjectData -> data.internalName
      is ModuleData -> data.internalName
      else -> null
    }
    val manager = ModuleManager.getInstance(project)
    val module = moduleName?.let { manager.findModuleByName(it) }
    return if (module?.isGradleModule() == true) module else null
  }

  private fun getSelectedDependencyData(context: DataContext): DependencyAnalyzerDependency.Data? {
    val selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(context)
    return when (val data = selectedNodes?.firstOrNull()?.data) {
      is ProjectData -> DAModule(data.internalName)
      is ModuleData -> DAModule(data.internalName)
      else -> null
    }
  }

  private fun Module.isGradleModule(): Boolean {
    return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)
  }
}