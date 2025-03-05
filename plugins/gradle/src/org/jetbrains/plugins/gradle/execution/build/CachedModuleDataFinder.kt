// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.project.GradleModuleDataIndex
import org.jetbrains.plugins.gradle.util.GradleModuleData

class CachedModuleDataFinder private constructor() {

  companion object {

    @JvmStatic
    fun getGradleModuleData(module: Module): GradleModuleData? =
      GradleModuleDataIndex.findGradleModuleData(module)

    @JvmStatic
    fun findMainModuleData(module: Module): DataNode<out ModuleData>? =
      ExternalSystemModuleDataIndex.findModuleNode(module)

    @JvmStatic
    fun findMainModuleData(project: Project, modulePath: String): DataNode<out ModuleData>? =
      ExternalSystemModuleDataIndex.findModuleNode(project, modulePath)

    @JvmStatic
    fun findModuleData(module: Module): DataNode<out ModuleData>? =
      GradleModuleDataIndex.findModuleNode(module)
  }
}
