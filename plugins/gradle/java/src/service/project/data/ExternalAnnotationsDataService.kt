// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library

@Order(value = ExternalSystemConstants.UNORDERED)
class ExternalAnnotationsDataService: AbstractProjectDataService<LibraryData, Library>() {
  override fun getTargetDataKey(): Key<LibraryData> = ProjectKeys.LIBRARY

  override fun postProcess(toImport: MutableCollection<DataNode<LibraryData>>,
                           projectData: ProjectData?,
                           project: Project,
                           modelsProvider: IdeModifiableModelsProvider) {

    val resolver = ExternalAnnotationsArtifactsResolver.EP_NAME.extensionList.firstOrNull() ?: return
    toImport.forEach {
      val libraryData = it.data
      val libraryName = libraryData.internalName
      val library = modelsProvider.getLibraryByName(libraryName)
      if (library != null) {
        resolver.resolveAsync(project, library, "${libraryData.groupId}:${libraryData.artifactId}:${libraryData.version}")
      }
    }
  }
}