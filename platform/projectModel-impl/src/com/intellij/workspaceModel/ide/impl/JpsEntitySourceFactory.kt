// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

object JpsEntitySourceFactory {
  fun createEntitySourceForModule(project: Project,
                                  baseModuleDir: VirtualFileUrl,
                                  externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = JpsFileEntitySource.FileInDirectory(baseModuleDir, location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createEntitySourceForProjectLibrary(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = createJpsEntitySourceForProjectLibrary(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createEntitySourceForArtifact(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = createJpsEntitySourceForArtifact(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  fun createJpsEntitySourceForProjectLibrary(configLocation: JpsProjectConfigLocation): JpsFileEntitySource {
    return createJpsEntitySource(configLocation, "libraries")
  }

  fun createJpsEntitySourceForArtifact(configLocation: JpsProjectConfigLocation): JpsFileEntitySource {
    return createJpsEntitySource(configLocation, "artifacts")
  }

  private fun createJpsEntitySource(configLocation: JpsProjectConfigLocation, directoryLocation: String) = when (configLocation) {
    is JpsProjectConfigLocation.DirectoryBased -> JpsFileEntitySource.FileInDirectory(configLocation.ideaFolder.append(directoryLocation),
                                                                                      configLocation)
    is JpsProjectConfigLocation.FileBased -> JpsFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
  }

}