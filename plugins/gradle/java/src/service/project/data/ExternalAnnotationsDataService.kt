// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.settings.GradleSettings

@Order(value = ExternalSystemConstants.UNORDERED)
class ExternalAnnotationsDataService: AbstractProjectDataService<LibraryData, Library>() {
  override fun getTargetDataKey(): Key<LibraryData> = ProjectKeys.LIBRARY

  override fun onSuccessImport(imported: MutableCollection<DataNode<LibraryData>>,
                               projectData: ProjectData?,
                               project: Project,
                               modelsProvider: IdeModelsProvider) {
    if (!Registry.`is`("external.system.import.resolve.annotations")) {
      return
    }

    projectData?.apply {
      GradleSettings
        .getInstance(project)
        .linkedProjectsSettings
        .find { settings -> settings.externalProjectPath == linkedExternalProjectPath }
        ?.let {
          if (!it.isResolveExternalAnnotations) {
            return@onSuccessImport
          }
        }
    }

    val resolver = ExternalAnnotationsArtifactsResolver.EP_NAME.extensionList.firstOrNull() ?: return
    val totalSize = imported.size.toDouble()

    runBackgroundableTask("Resolving external annotations", project) { indicator ->
      indicator.isIndeterminate = false
      imported.forEachIndexed { index, dataNode ->
        if (indicator.isCanceled) {
          return@runBackgroundableTask
        }
        indicator.fraction = (index.toDouble() + 1) / totalSize
        val libraryData = dataNode.data
        val libraryName = libraryData.internalName
        val library = modelsProvider.getLibraryByName(libraryName)
        if (library != null) {
          indicator.text = "Looking for annotations for '$libraryName'"
          val mavenId = "${libraryData.groupId}:${libraryData.artifactId}:${libraryData.version}"
          resolver.resolve(project, library, mavenId)
        }
      }
    }
  }
  companion object {
    val LOG = Logger.getInstance(ExternalAnnotationsDataService::class.java)
  }
}