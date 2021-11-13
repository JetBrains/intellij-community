// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemContentRootContributor
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil.findGradleModuleData

class GradleContentRootContributor : ExternalSystemContentRootContributor {
  override fun isApplicable(systemId: String): Boolean = systemId == GradleConstants.SYSTEM_ID.id

  override fun findContentRoots(
    module: Module,
    sourceTypes: Collection<ExternalSystemSourceType>,
  ): Collection<ExternalSystemContentRootContributor.ExternalContentRoot> = mutableListOf<ExternalSystemContentRootContributor.ExternalContentRoot>().apply {
    processContentRoots(module) { rootData ->
      for (sourceType in sourceTypes) {
        rootData.getPaths(sourceType).mapTo(this) {
          ExternalSystemContentRootContributor.ExternalContentRoot(it.path, sourceType)
        }
      }
    }
  }.toList()

  companion object {
    internal fun processContentRoots(
      module: Module,
      processor: (ContentRootData) -> Unit,
    ) {
      val moduleData = findGradleModuleData(module) ?: return
      moduleData.processModule(processor)
      for (eachSourceSetNode in ExternalSystemApiUtil.getChildren(moduleData, GradleSourceSetData.KEY)) {
        eachSourceSetNode.processModule(processor)
      }
    }
  }
}

private fun DataNode<out ModuleData>.processModule(processor: (ContentRootData) -> Unit) {
  for (eachContentRootNode in ExternalSystemApiUtil.findAll(this, ProjectKeys.CONTENT_ROOT)) {
    processor(eachContentRootNode.data)
  }
}