// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData

/**
 * @author Vladislav.Soroka
 */
class CachedModuleDataFinder private constructor(private val project: Project) {

  private fun findGradleModuleDataImpl(module: Module): GradleModuleData? {
    val moduleData = findMainModuleDataImpl(module) ?: return null
    return GradleModuleData(moduleData)
  }

  private fun findModuleDataImpl(module: Module): DataNode<out ModuleData>? {
    val moduleType = ExternalSystemApiUtil.getExternalModuleType(module)
    if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY != moduleType) {
      return findMainModuleDataImpl(module)
    }

    val projectId = ExternalSystemApiUtil.getExternalProjectId(module) ?: return null
    val cache = getDataStorageCachedValue(project, module, ::collectAllSourceSetData)
    return cache[projectId]
  }

  private fun findMainModuleDataImpl(module: Module): DataNode<out ModuleData>? {
    ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
    ExternalSystemApiUtil.getExternalProjectId(module) ?: return null

    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return findMainModuleDataImpl(module.project, externalProjectPath)
  }

  private fun findMainModuleDataImpl(project: Project, modulePath: String): DataNode<out ModuleData>? {
    val cache = getDataStorageCachedValue(project, project, ::collectAllModuleData)
    return cache[modulePath]
  }

  companion object {

    @JvmStatic
    fun getGradleModuleData(module: Module): GradleModuleData? {
      return CachedModuleDataFinder(module.project).findGradleModuleDataImpl(module)
    }

    @JvmStatic
    fun findMainModuleData(module: Module): DataNode<out ModuleData>? {
      return CachedModuleDataFinder(module.project).findMainModuleDataImpl(module)
    }

    @JvmStatic
    fun findMainModuleData(project: Project, modulePath: String): DataNode<out ModuleData>? {
      return CachedModuleDataFinder(project).findMainModuleDataImpl(project, modulePath)
    }

    @JvmStatic
    fun findModuleData(module: Module): DataNode<out ModuleData>? {
      return CachedModuleDataFinder(module.project).findModuleDataImpl(module)
    }

    @JvmStatic
    private fun collectAllSourceSetData(module: Module): Map<String, DataNode<out GradleSourceSetData>> {
      val mainModuleDataNode = findMainModuleData(module) ?: return emptyMap()
      val sourceSetDataNodes = ExternalSystemApiUtil.getChildren(mainModuleDataNode, GradleSourceSetData.KEY)
      return sourceSetDataNodes.associateBy { it.data.id }
    }

    @JvmStatic
    private fun collectAllModuleData(project: Project): Map<String, DataNode<out ModuleData>> {
      val projectDataManager = ProjectDataManager.getInstance()
      val projectInfos = projectDataManager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
      val projectDataNodes = projectInfos.mapNotNull { it.externalProjectStructure }
      val moduleDataNodes = projectDataNodes.flatMap { ExternalSystemApiUtil.getChildren(it, ProjectKeys.MODULE) }
      return moduleDataNodes.associateBy { it.data.linkedExternalProjectPath }
    }

    @JvmStatic
    private fun <H : UserDataHolder, T> getDataStorageCachedValue(project: Project, dataHolder: H, createValue: (H) -> T): T {
      return CachedValuesManager.getManager(project).getCachedValue(dataHolder) {
        CachedValueProvider.Result.create(createValue(dataHolder), ExternalProjectsDataStorage.getInstance(project))
      }
    }
  }
}
