// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.openapi.module.Module

class GradleCoordinateContributor : ExternalSystemCoordinateContributor {

  override fun findModuleCoordinate(module: Module): ProjectCoordinate? {
    val moduleData = GradleModuleDataIndex.findModuleNode(module) ?: return null
    return moduleData.getData().publication
  }
}
