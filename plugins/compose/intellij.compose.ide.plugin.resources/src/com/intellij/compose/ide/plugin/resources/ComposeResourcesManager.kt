// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.compose.ide.plugin.shared.associateNotNull
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.pathString

/**
 * Manage Compose resource directories across modules in a project.
 *
 * Provides a mechanism to retrieve and update Compose resource directories identified on a per-module basis.
 */
@Service(Level.PROJECT)
internal class ComposeResourcesManager(private val project: Project) {
  private val myComposeResourcesByModulePath = AtomicReference<Map<String, ComposeResources>?>()
  val composeResources: List<ComposeResourcesDir> get() = composeResourcesByModulePath.flatMap { it.value.directoriesBySourceSetName.values }

  val composeResourcesByModulePath: Map<String, ComposeResources>
    get() = myComposeResourcesByModulePath.updateAndGet { cached -> cached ?: loadComposeResources() }.orEmpty()

  fun refresh() {
    myComposeResourcesByModulePath.updateAndGet { loadComposeResources() }
  }

  /** if the path is inside a composeResources dir, return the dir, otherwise null */
  fun findComposeResourcesDirFor(path: String): ComposeResourcesDir? =
    composeResources.firstOrNull { path.startsWith(it.directoryPath.pathString) }

  /** Retrieves all `ComposeResourcesModel` data nodes associated with the current project
   * in case of multiple modules having compose resources */
  private val composeResourcesModels: Collection<DataNode<ComposeResourcesModel>>
    get() {
      val externalProjectData = ProjectDataManager.getInstance()
                                  .getExternalProjectsData(project, SYSTEM_ID)
                                  .firstOrNull() ?: return emptyList()
      val externalProjectStructure = externalProjectData.externalProjectStructure ?: return emptyList()
      return ExternalSystemApiUtil.findAllRecursively(externalProjectStructure, COMPOSE_RESOURCES_KEY)
    }


  private fun loadComposeResources(): Map<String, ComposeResources> =
    composeResourcesModels.associateNotNull { node ->
      val moduleName = (node.parent?.data as? ModuleData)?.moduleName ?: return@associateNotNull null
      val dirs = node.data.customComposeResourcesDirs.mapValues { (sourceSetName, customDirectoryPath) ->
        val (directoryPath, isCustom) = customDirectoryPath
        ComposeResourcesDir(moduleName, sourceSetName, Path.of(directoryPath), isCustom)
      }
      moduleName to ComposeResources(
        moduleName = moduleName,
        directoriesBySourceSetName = dirs,
        isPublicResClass = node.data.isPublicResClass,
        nameOfResClass = node.data.nameOfResClass,
      )
    }

  /** Refresh Compose resources directories after project import */
  internal class ComposeResourcesProjectDataImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      project.service<ComposeResourcesManager>().refresh()
    }
  }
}