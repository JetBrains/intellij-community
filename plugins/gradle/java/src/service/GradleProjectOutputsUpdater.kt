// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleProjectOutputsUpdater : ExternalSystemTaskNotificationListener {

  private val projectPaths = HashMap<ExternalSystemTaskId, String>()

  override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
    if (!Registry.`is`("gradle.refresh.project.outputs")) return
    if (id.type != ExternalSystemTaskType.EXECUTE_TASK) return
    projectPaths[id] = workingDir ?: return
  }

  override fun onEnd(id: ExternalSystemTaskId) {
    projectPaths.remove(id)
  }

  override fun onSuccess(id: ExternalSystemTaskId) {
    val project = id.findProject() ?: return
    val projectPath = projectPaths.get(id) ?: return

    val projectNode = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, projectPath) ?: return
    val moduleNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)
    val sourceSetNodes = moduleNodes.flatMap { ExternalSystemApiUtil.findAll(it, GradleSourceSetData.KEY) }

    val modelsProvider = IdeModelsProviderImpl(project)
    val affectedModules = (moduleNodes + sourceSetNodes).mapNotNull { modelsProvider.findIdeModule(it.data) }

    val affectedRoots = CompilerPaths.getOutputPaths(affectedModules.toTypedArray()).toSet()
    CompilerUtil.refreshOutputRoots(affectedRoots)
  }
}