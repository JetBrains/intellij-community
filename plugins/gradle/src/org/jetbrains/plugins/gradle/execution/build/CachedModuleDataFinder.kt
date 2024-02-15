// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Vladislav.Soroka
 */
class CachedModuleDataFinder private constructor(private val project: Project) {

  fun findGradleModuleData(module: Module): GradleModuleData? {
    val moduleData = findMainModuleData(module) ?: return null
    return GradleModuleData(moduleData)
  }

  private fun findModuleData(module: Module): DataNode<out ModuleData>? {
    val moduleType = ExternalSystemApiUtil.getExternalModuleType(module)
    if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY != moduleType) {
      return findMainModuleData(module)
    }

    val projectId = ExternalSystemApiUtil.getExternalProjectId(module) ?: return null
    val cachedNode = getCache(project)[projectId]
    if (cachedNode != null) return cachedNode

    val mainModuleData = findMainModuleData(module) ?: return null
    return ExternalSystemApiUtil.findChild(mainModuleData, GradleSourceSetData.KEY) {
      val id = it.data.id
      getCache(project)[id] = it
      StringUtil.equals(projectId, id)
    }
  }

  fun findMainModuleData(module: Module): DataNode<out ModuleData>? {
    ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
    ExternalSystemApiUtil.getExternalProjectId(module) ?: return null

    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return findModuleData(module.project, externalProjectPath)
  }

  fun findModuleData(project: Project, modulePath: String): DataNode<out ModuleData>? {
    val cachedNode = getCache(project)[modulePath]
    if (cachedNode != null) return cachedNode

    val projectNode = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, modulePath) ?: return null
    return ExternalSystemApiUtil.findChild(projectNode, ProjectKeys.MODULE) {
      val externalProjectPath = it.data.linkedExternalProjectPath
      getCache(project)[externalProjectPath] = it
      modulePath == externalProjectPath
    }
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): CachedModuleDataFinder {
      return CachedModuleDataFinder(project)
    }

    @JvmStatic
    fun getGradleModuleData(module: Module): GradleModuleData? {
      return getInstance(module.project).findGradleModuleData(module)
    }

    fun findModuleData(project: Project, modulePath: String): DataNode<out ModuleData>? {
      return getInstance(project).findModuleData(project, modulePath)
    }

    @JvmStatic
    private fun getCache(project: Project): ConcurrentHashMap<String, DataNode<out ModuleData>> {
      return CachedValuesManager.getManager(project).getCachedValue(project) {
        CachedValueProvider.Result.create(ConcurrentHashMap(), ExternalProjectsDataStorage.getInstance(project))
      }
    }
  }
}
