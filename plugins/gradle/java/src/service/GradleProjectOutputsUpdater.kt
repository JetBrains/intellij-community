// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.task.GradleTaskResultListener
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleProjectOutputsUpdater: GradleTaskResultListener {
  override fun onSuccess(id: ExternalSystemTaskId, projectPath: String) {
    if (!Registry.`is`("gradle.refresh.project.outputs")) return

    val ideaProject = id.findProject()
    if (ideaProject == null) {
      LOG.warn("Project path [$projectPath] does not belong to any open Gradle projects")
      return
    }

    val modelsProvider = IdeModelsProviderImpl(ideaProject)
    val projectNode = ExternalSystemApiUtil.findProjectNode(ideaProject, GradleConstants.SYSTEM_ID, projectPath) ?: return
    val moduleNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)
    val affectedModules: List<Module> = moduleNodes.flatMap { ExternalSystemApiUtil.findAll(it, GradleSourceSetData.KEY) + it }
      .map { it.data }
      .filterIsInstance(ModuleData::class.java)
      .mapNotNull { modelsProvider.findIdeModule(it) }

    val affectedRoots = ContainerUtil.newHashSet(*CompilerPaths.getOutputPaths(affectedModules.toTypedArray()))
    CompilerUtil.refreshOutputRoots(affectedRoots)
  }

  companion object {
    private val LOG = logger<GradleProjectOutputsUpdater>()
  }
}