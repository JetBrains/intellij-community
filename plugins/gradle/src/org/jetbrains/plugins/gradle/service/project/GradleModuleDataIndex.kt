// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex.getDataStorageCachedValue
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData

object GradleModuleDataIndex {

  private val SOURCE_SET_NODE_KEY = Key.create<CachedValue<Map<String, DataNode<out GradleSourceSetData>>>>("GradleModuleDataIndex")

  @JvmStatic
  fun findGradleModuleData(module: Module): GradleModuleData? {
    val moduleDataNode = ExternalSystemModuleDataIndex.findModuleNode(module) ?: return null
    if (moduleDataNode.data.owner != GradleConstants.SYSTEM_ID) return null
    return GradleModuleData(moduleDataNode)
  }

  @JvmStatic
  fun findModuleNode(module: Module): DataNode<out ModuleData>? {
    val moduleType = ExternalSystemApiUtil.getExternalModuleType(module)
    if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY != moduleType) {
      return ExternalSystemModuleDataIndex.findModuleNode(module)
    }

    val projectId = ExternalSystemApiUtil.getExternalProjectId(module) ?: return null
    val cache = getDataStorageCachedValue(module.project, module, SOURCE_SET_NODE_KEY, ::collectAllSourceSetNodes)
    return cache[projectId]
  }

  @JvmStatic
  private fun collectAllSourceSetNodes(module: Module): Map<String, DataNode<out GradleSourceSetData>> {
    val moduleNode = ExternalSystemModuleDataIndex.findModuleNode(module) ?: return emptyMap()
    val sourceSetNodes = ExternalSystemApiUtil.getChildren(moduleNode, GradleSourceSetData.KEY)
    return sourceSetNodes.associateBy { it.data.id }
  }
}