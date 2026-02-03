// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class VersionCatalogsLocator(val myProject: Project) {
  fun getVersionCatalogsForModule(module: Module): Map<String, Path> {
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return emptyMap()
    val projectInfo = ProjectDataManager.getInstance()
                        .getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, externalProjectPath)
                      ?: return emptyMap()
    val versionCatalogsModel = projectInfo.externalProjectStructure?.let {
      ExternalSystemApiUtil.find(it, BuildScriptClasspathData.VERSION_CATALOGS)?.data
    }
                               ?: return emptyMap()
    return versionCatalogsModel.catalogsLocations.mapValues { entry -> Path.of(entry.value) }
  }
}