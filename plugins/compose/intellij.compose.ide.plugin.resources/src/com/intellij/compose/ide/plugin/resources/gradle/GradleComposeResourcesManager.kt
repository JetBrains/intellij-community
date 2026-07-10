// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.gradle

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.compose.ide.plugin.shared.associateNotNull
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Manage Compose resource directories across modules in a project.
 *
 * Provides a mechanism to retrieve and update Compose resource directories identified on a per-module basis.
 */
@Service(Level.PROJECT)
internal class GradleComposeResourcesManager(private val project: Project) {
  private val myComposeResourcesByModulePath = AtomicReference<Map<String, GradleComposeResources>?>()

  val composeResourcesByModulePath: Map<String, GradleComposeResources>
    get() = myComposeResourcesByModulePath.updateAndGet { cached -> cached ?: loadComposeResources() }.orEmpty()

  fun refresh() {
    myComposeResourcesByModulePath.updateAndGet { loadComposeResources() }
  }

  /** Retrieves all `ComposeResourcesModel` data nodes associated with the current project
   * in case of multiple modules having compose resources */
  private val composeResourcesModels: Collection<DataNode<ComposeResourcesModel>>?
    get() {
      val externalProjectData = ProjectDataManager.getInstance()
        .getExternalProjectsData(project, SYSTEM_ID)
      if (externalProjectData.isEmpty()) return null // Returns null so the AtomicReference doesn't cache it

      val models = externalProjectData
        .mapNotNull { it.externalProjectStructure }
        .flatMap { ExternalSystemApiUtil.findAllRecursively(it, COMPOSE_RESOURCES_KEY) }
      log.info("ComposeResources: Scanned ${externalProjectData.size} projects, found ${models.size} resource models.")
      return models
    }

  private fun loadComposeResources(): Map<String, GradleComposeResources>? =
    composeResourcesModels?.associateNotNull { node ->
      val moduleData = node.parent?.data as? ModuleData ?: return@associateNotNull null
      val moduleName = moduleData.moduleName
      val projectGroupName = moduleData.group.orEmpty()
      val dirs = node.data.customComposeResourcesDirs.mapValues { (sourceSetName, customDirectoryPath) ->
        val (directoryPath, isCustom) = customDirectoryPath
        GradleComposeResourcesDir(moduleName, sourceSetName, Path.of(directoryPath), projectGroupName, isCustom)
      }
      moduleName to GradleComposeResources(
        moduleName = moduleName,
        directoriesBySourceSetName = dirs,
        isPublicResClass = node.data.isPublicResClass,
        nameOfResClass = node.data.nameOfResClass,
        packageOfResClass = node.data.packageOfResClass
      )
    }

  /** Refresh Compose resources directories after project import */
  internal class ComposeResourcesProjectDataImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      project.service<GradleComposeResourcesManager>().refresh()
    }
  }

  companion object {
    private val log = logger<GradleComposeResourcesManager>()
  }
}
